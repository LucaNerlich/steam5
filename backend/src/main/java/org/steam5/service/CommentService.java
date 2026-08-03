package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.steam5.domain.Comment;
import org.steam5.domain.CommentModerator;
import org.steam5.domain.CommentReaction;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.ReactionType;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.User;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.CommentReactionRepository;
import org.steam5.repository.CommentRepository;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int LIST_PAGE_SIZE = 100;

    /** Maximum number of resolved reactor names attached to each {@link ReactionDto}. */
    private static final int MAX_REACTORS_PER_TYPE = 5;

    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final GuessRepository guessRepository;
    private final ReviewGamePickRepository pickRepository;
    private final UserRepository userRepository;
    private final DomainCacheEvictor cacheEvictor;

    /**
     * Creates a comment for the current UTC game day after the user has completed it.
     *
     * @throws ReviewGameException if the date is not today's game day, or the day is incomplete
     */
    @Transactional
    public CommentDto createComment(final String steamId, final LocalDate gameDate, final String body) {
        requireCurrentGameDay(gameDate);

        final List<ReviewGamePick> picks = pickRepository.findByPickDate(gameDate);
        if (picks.isEmpty()) {
            throw new ReviewGameException(400, "day_not_complete");
        }
        final Set<Long> pickAppIds = picks.stream()
                .map(ReviewGamePick::getAppId)
                .collect(Collectors.toSet());
        final Set<Long> completedPickAppIds = guessRepository.findAllForDay(steamId, gameDate).stream()
                .map(Guess::getAppId)
                .filter(pickAppIds::contains)
                .collect(Collectors.toSet());
        if (completedPickAppIds.size() < pickAppIds.size()) {
            throw new ReviewGameException(400, "day_not_complete");
        }

        final Comment comment = new Comment();
        comment.setSteamId(steamId);
        comment.setGameDate(gameDate);
        comment.setBody(body.trim());
        comment.setArchived(false);
        final Comment saved = commentRepository.save(comment);
        evictCommentsForDayAfterCommit(gameDate);
        return toDto(saved, Map.of(), Set.of());
    }

    /**
     * Lists unarchived comments for a game date (newest first), with reactions and authors.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "comments-for-day", key = "#gameDate.toString() + ':' + (#viewerSteamId != null ? #viewerSteamId : 'anon')")
    public List<CommentDto> listComments(final LocalDate gameDate, final String viewerSteamId) {
        final List<Comment> comments = commentRepository.findByGameDateAndArchivedFalseOrderByCreatedAtDesc(
                gameDate, PageRequest.of(0, LIST_PAGE_SIZE));
        if (comments.isEmpty()) {
            return List.of();
        }

        final List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        final Map<Long, Map<ReactionType, Long>> countsByComment = new HashMap<>();
        for (final CommentReactionRepository.ReactionCountRow row
                : commentReactionRepository.countByCommentIds(commentIds)) {
            countsByComment
                    .computeIfAbsent(row.getCommentId(), id -> new EnumMap<>(ReactionType.class))
                    .put(row.getReactionType(), row.getReactionCount());
        }

        final Map<Long, Set<ReactionType>> viewerReactionsByComment = new HashMap<>();
        if (viewerSteamId != null && !viewerSteamId.isBlank()) {
            for (final CommentReaction reaction
                    : commentReactionRepository.findByComment_IdInAndSteamId(commentIds, viewerSteamId)) {
                viewerReactionsByComment
                        .computeIfAbsent(reaction.getComment().getId(), id -> new HashSet<>())
                        .add(reaction.getReactionType());
            }
        }

        final Map<Long, Map<ReactionType, List<String>>> reactorIdsByComment = new HashMap<>();
        final Set<String> reactorSteamIds = new HashSet<>();
        for (final CommentReaction row
                : commentReactionRepository.findByComment_IdInOrderByCreatedAtAscIdAsc(commentIds)) {
            reactorIdsByComment
                    .computeIfAbsent(row.getComment().getId(), id -> new EnumMap<>(ReactionType.class))
                    .computeIfAbsent(row.getReactionType(), type -> new ArrayList<>())
                    .add(row.getSteamId());
            reactorSteamIds.add(row.getSteamId());
        }

        final Set<String> authorIds = comments.stream().map(Comment::getSteamId).collect(Collectors.toSet());
        final Set<String> userIdsToResolve = new HashSet<>(authorIds);
        userIdsToResolve.addAll(reactorSteamIds);
        final Map<String, User> usersById = userRepository.findAllById(userIdsToResolve).stream()
                .collect(Collectors.toMap(User::getSteamId, u -> u, (a, b) -> a));

        final List<CommentDto> result = new ArrayList<>(comments.size());
        for (final Comment comment : comments) {
            result.add(toDto(
                    comment,
                    countsByComment.getOrDefault(comment.getId(), Map.of()),
                    viewerReactionsByComment.getOrDefault(comment.getId(), Set.of()),
                    usersById.get(comment.getSteamId()),
                    reactorIdsByComment.getOrDefault(comment.getId(), Map.of()),
                    usersById
            ));
        }
        return result;
    }

    /**
     * Toggles a reaction on a comment for the current UTC game day.
     */
    @Transactional
    public List<ReactionDto> toggleReaction(final Long commentId, final String steamId, final ReactionType reactionType) {
        final Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ReviewGameException(404, "comment_not_found"));
        if (comment.isArchived()) {
            throw new ReviewGameException(404, "comment_not_found");
        }
        requireCurrentGameDay(comment.getGameDate());

        final var existing = commentReactionRepository.findByComment_IdAndSteamIdAndReactionType(
                commentId, steamId, reactionType);
        if (existing.isPresent()) {
            commentReactionRepository.delete(existing.get());
        } else {
            commentReactionRepository.insertIgnoreConflict(
                    commentId, steamId, reactionType.name(), OffsetDateTime.now());
        }

        evictCommentsForDayAfterCommit(comment.getGameDate());
        return buildReactionDtos(commentId, steamId);
    }

    /**
     * Soft-archives a comment so it no longer appears in public lists.
     *
     * @throws ReviewGameException 403 when the caller is not the hardcoded moderator
     */
    @Transactional
    public void archiveComment(final Long commentId, final String steamId) {
        if (!CommentModerator.isModerator(steamId)) {
            // Do not log raw Steam IDs on rejected authz attempts — correlate via request ID.
            final String correlationId = MDC.get("correlationId");
            log.warn("Rejected comment archive: commentId={} correlationId={}",
                    commentId, correlationId != null ? correlationId : "n/a");
            throw new ReviewGameException(403, "forbidden");
        }
        final Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ReviewGameException(404, "comment_not_found"));
        if (comment.isArchived()) {
            return;
        }
        comment.setArchived(true);
        comment.setArchivedAt(OffsetDateTime.now());
        commentRepository.save(comment);
        final String correlationId = MDC.get("correlationId");
        log.info("Archived comment: commentId={} correlationId={}",
                commentId, correlationId != null ? correlationId : "n/a");
        evictCommentsForDayAfterCommit(comment.getGameDate());
    }

    /**
     * Ensures {@code gameDate} matches {@link GameDate#todayUtc()} — the same calendar
     * anchor used for daily picks (UTC midnight / ~02:00 CEST), not the viewer's local clock.
     */
    private static void requireCurrentGameDay(final LocalDate gameDate) {
        if (!gameDate.equals(GameDate.todayUtc())) {
            throw new ReviewGameException(400, "not_current_game_day");
        }
    }

    private List<ReactionDto> buildReactionDtos(final Long commentId, final String viewerSteamId) {
        final Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        for (final CommentReactionRepository.ReactionCountRow row
                : commentReactionRepository.countByCommentIds(List.of(commentId))) {
            counts.put(row.getReactionType(), row.getReactionCount());
        }
        final Set<ReactionType> viewerHeld = new HashSet<>();
        for (final CommentReaction reaction
                : commentReactionRepository.findByComment_IdInAndSteamId(List.of(commentId), viewerSteamId)) {
            viewerHeld.add(reaction.getReactionType());
        }

        final Map<ReactionType, List<String>> reactorIdsByType = new EnumMap<>(ReactionType.class);
        final Set<String> reactorSteamIds = new HashSet<>();
        for (final CommentReaction row
                : commentReactionRepository.findByComment_IdInOrderByCreatedAtAscIdAsc(List.of(commentId))) {
            reactorIdsByType.computeIfAbsent(row.getReactionType(), type -> new ArrayList<>()).add(row.getSteamId());
            reactorSteamIds.add(row.getSteamId());
        }
        final Map<String, User> usersById = userRepository.findAllById(reactorSteamIds).stream()
                .collect(Collectors.toMap(User::getSteamId, u -> u, (a, b) -> a));

        return reactionDtos(counts, viewerHeld, reactorIdsByType, usersById);
    }

    private CommentDto toDto(final Comment comment,
                             final Map<ReactionType, Long> counts,
                             final Set<ReactionType> viewerHeld) {
        final User user = userRepository.findById(comment.getSteamId()).orElse(null);
        return toDto(comment, counts, viewerHeld, user, Map.of(), Map.of());
    }

    private CommentDto toDto(final Comment comment,
                             final Map<ReactionType, Long> counts,
                             final Set<ReactionType> viewerHeld,
                             final User user,
                             final Map<ReactionType, List<String>> reactorIdsByType,
                             final Map<String, User> usersById) {
        return new CommentDto(
                comment.getId(),
                comment.getBody(),
                comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null,
                toAuthor(comment.getSteamId(), user),
                reactionDtos(counts, viewerHeld, reactorIdsByType, usersById)
        );
    }

    private static AuthorDto toAuthor(final String steamId, final User user) {
        if (user == null) {
            return new AuthorDto(steamId, steamId, null);
        }
        final String avatar = (user.getAvatarFull() != null && !user.getAvatarFull().isBlank())
                ? user.getAvatarFull()
                : user.getAvatar();
        return new AuthorDto(user.getSteamId(), resolveDisplayName(steamId, user), avatar);
    }

    /** Prefers the user's persona name, falling back to the raw Steam ID when unresolved or blank. */
    private static String resolveDisplayName(final String steamId, final User user) {
        if (user == null) {
            return steamId;
        }
        return (user.getPersonaName() != null && !user.getPersonaName().isBlank())
                ? user.getPersonaName()
                : user.getSteamId();
    }

    private static List<ReactionDto> reactionDtos(final Map<ReactionType, Long> counts,
                                                  final Set<ReactionType> viewerHeld,
                                                  final Map<ReactionType, List<String>> reactorIdsByType,
                                                  final Map<String, User> usersById) {
        final List<ReactionDto> reactions = new ArrayList<>(ReactionType.values().length);
        for (final ReactionType type : ReactionType.values()) {
            final List<String> reactorIds = reactorIdsByType.getOrDefault(type, List.of());
            final List<String> reactorNames = reactorIds.stream()
                    .limit(MAX_REACTORS_PER_TYPE)
                    .map(steamId -> resolveDisplayName(steamId, usersById.get(steamId)))
                    .toList();
            reactions.add(new ReactionDto(
                    type.name(),
                    counts.getOrDefault(type, 0L),
                    viewerHeld.contains(type),
                    reactorNames
            ));
        }
        return reactions;
    }

    private void evictCommentsForDayAfterCommit(final LocalDate day) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheEvictor.evictCommentsForDay(day);
                }
            });
        } else {
            cacheEvictor.evictCommentsForDay(day);
        }
    }

    public record AuthorDto(String steamId, String personaName, String avatar) {
    }

    public record ReactionDto(String reactionType, long count, boolean reactedByViewer, List<String> reactors) {
    }

    public record CommentDto(
            Long id,
            String body,
            String createdAt,
            AuthorDto author,
            List<ReactionDto> reactions
    ) {
    }
}
