package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.steam5.domain.Comment;
import org.steam5.domain.CommentReaction;
import org.steam5.domain.ReactionType;
import org.steam5.domain.User;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.CommentReactionRepository;
import org.steam5.repository.CommentRepository;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int LIST_PAGE_SIZE = 100;

    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final GuessRepository guessRepository;
    private final ReviewGamePickRepository pickRepository;
    private final UserRepository userRepository;
    private final DomainCacheEvictor cacheEvictor;

    @Transactional
    public CommentDto createComment(final String steamId, final LocalDate gameDate, final String body) {
        final int guessedRounds = guessRepository.findAllForDay(steamId, gameDate).size();
        final int pickCount = pickRepository.findByPickDate(gameDate).size();
        if (pickCount <= 0 || guessedRounds < pickCount) {
            throw new ReviewGameException(400, "day_not_complete");
        }

        final Comment comment = new Comment();
        comment.setSteamId(steamId);
        comment.setGameDate(gameDate);
        comment.setBody(body.trim());
        final Comment saved = commentRepository.save(comment);
        evictCommentsForDayAfterCommit(gameDate);
        return toDto(saved, Map.of(), Set.of());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "comments-for-day", key = "#gameDate.toString() + ':' + (#viewerSteamId != null ? #viewerSteamId : 'anon')")
    public List<CommentDto> listComments(final LocalDate gameDate, final String viewerSteamId) {
        final List<Comment> comments = commentRepository.findByGameDateOrderByCreatedAtDesc(
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
            for (final CommentReaction reaction : commentReactionRepository.findByComment_IdIn(commentIds)) {
                if (!viewerSteamId.equals(reaction.getSteamId())) {
                    continue;
                }
                viewerReactionsByComment
                        .computeIfAbsent(reaction.getComment().getId(), id -> new HashSet<>())
                        .add(reaction.getReactionType());
            }
        }

        final Set<String> authorIds = comments.stream().map(Comment::getSteamId).collect(Collectors.toSet());
        final Map<String, User> usersById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getSteamId, u -> u, (a, b) -> a));

        final List<CommentDto> result = new ArrayList<>(comments.size());
        for (final Comment comment : comments) {
            result.add(toDto(
                    comment,
                    countsByComment.getOrDefault(comment.getId(), Map.of()),
                    viewerReactionsByComment.getOrDefault(comment.getId(), Set.of()),
                    usersById.get(comment.getSteamId())
            ));
        }
        return result;
    }

    @Transactional
    public List<ReactionDto> toggleReaction(final Long commentId, final String steamId, final ReactionType reactionType) {
        final Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ReviewGameException(404, "comment_not_found"));

        final var existing = commentReactionRepository.findByComment_IdAndSteamIdAndReactionType(
                commentId, steamId, reactionType);
        if (existing.isPresent()) {
            commentReactionRepository.delete(existing.get());
        } else {
            final CommentReaction reaction = new CommentReaction();
            reaction.setComment(comment);
            reaction.setSteamId(steamId);
            reaction.setReactionType(reactionType);
            commentReactionRepository.save(reaction);
        }

        evictCommentsForDayAfterCommit(comment.getGameDate());
        return buildReactionDtos(commentId, steamId);
    }

    private List<ReactionDto> buildReactionDtos(final Long commentId, final String viewerSteamId) {
        final Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        for (final CommentReactionRepository.ReactionCountRow row
                : commentReactionRepository.countByCommentIds(List.of(commentId))) {
            counts.put(row.getReactionType(), row.getReactionCount());
        }
        final Set<ReactionType> viewerHeld = new HashSet<>();
        for (final CommentReaction reaction : commentReactionRepository.findByComment_IdIn(List.of(commentId))) {
            if (viewerSteamId.equals(reaction.getSteamId())) {
                viewerHeld.add(reaction.getReactionType());
            }
        }
        return reactionDtos(counts, viewerHeld);
    }

    private CommentDto toDto(final Comment comment,
                             final Map<ReactionType, Long> counts,
                             final Set<ReactionType> viewerHeld) {
        final User user = userRepository.findById(comment.getSteamId()).orElse(null);
        return toDto(comment, counts, viewerHeld, user);
    }

    private CommentDto toDto(final Comment comment,
                             final Map<ReactionType, Long> counts,
                             final Set<ReactionType> viewerHeld,
                             final User user) {
        return new CommentDto(
                comment.getId(),
                comment.getBody(),
                comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null,
                toAuthor(comment.getSteamId(), user),
                reactionDtos(counts, viewerHeld)
        );
    }

    private static AuthorDto toAuthor(final String steamId, final User user) {
        if (user == null) {
            return new AuthorDto(steamId, steamId, null, null);
        }
        final String personaName = (user.getPersonaName() != null && !user.getPersonaName().isBlank())
                ? user.getPersonaName()
                : user.getSteamId();
        final String avatar = (user.getAvatarFull() != null && !user.getAvatarFull().isBlank())
                ? user.getAvatarFull()
                : user.getAvatar();
        final String avatarBlurdata = (user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank())
                ? user.getBlurdataAvatarFull()
                : user.getBlurdataAvatar();
        return new AuthorDto(user.getSteamId(), personaName, avatar, avatarBlurdata);
    }

    private static List<ReactionDto> reactionDtos(final Map<ReactionType, Long> counts,
                                                  final Set<ReactionType> viewerHeld) {
        final List<ReactionDto> reactions = new ArrayList<>(ReactionType.values().length);
        for (final ReactionType type : ReactionType.values()) {
            reactions.add(new ReactionDto(
                    type.name(),
                    counts.getOrDefault(type, 0L),
                    viewerHeld.contains(type)
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

    public record AuthorDto(String steamId, String personaName, String avatar, String avatarBlurdata) {
    }

    public record ReactionDto(String reactionType, long count, boolean reactedByViewer) {
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
