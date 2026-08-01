package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.steam5.domain.Comment;
import org.steam5.domain.CommentReaction;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentServiceTest {

    private CommentRepository commentRepository;
    private CommentReactionRepository commentReactionRepository;
    private GuessRepository guessRepository;
    private ReviewGamePickRepository pickRepository;
    private UserRepository userRepository;
    private DomainCacheEvictor cacheEvictor;
    private CommentService service;

    private final LocalDate day = LocalDate.of(2026, 7, 30);

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        commentReactionRepository = mock(CommentReactionRepository.class);
        guessRepository = mock(GuessRepository.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        userRepository = mock(UserRepository.class);
        cacheEvictor = mock(DomainCacheEvictor.class);
        service = new CommentService(
                commentRepository,
                commentReactionRepository,
                guessRepository,
                pickRepository,
                userRepository,
                cacheEvictor
        );
    }

    @Test
    void createComment_throwsWhenDayIncomplete() {
        when(pickRepository.findByPickDate(day)).thenReturn(List.of(
                pick(1L, 10L), pick(2L, 20L)
        ));
        when(guessRepository.findAllForDay("u1", day)).thenReturn(List.of(
                guess(1L, 1)
        ));

        ReviewGameException ex = assertThrows(ReviewGameException.class,
                () -> service.createComment("u1", day, "nice day"));

        assertEquals(400, ex.getStatusCode());
        assertEquals("day_not_complete", ex.getMessage());
        verify(commentRepository, never()).save(any());
        verify(cacheEvictor, never()).evictCommentsForDay(any());
    }

    @Test
    void createComment_throwsWhenNoPicksExist() {
        when(pickRepository.findByPickDate(day)).thenReturn(List.of());
        when(guessRepository.findAllForDay("u1", day)).thenReturn(List.of());

        ReviewGameException ex = assertThrows(ReviewGameException.class,
                () -> service.createComment("u1", day, "hello"));

        assertEquals(400, ex.getStatusCode());
        assertEquals("day_not_complete", ex.getMessage());
    }

    @Test
    void createComment_persistsAndEvictsWhenDayComplete() {
        when(pickRepository.findByPickDate(day)).thenReturn(List.of(pick(1L, 10L), pick(2L, 20L)));
        when(guessRepository.findAllForDay("u1", day)).thenReturn(List.of(guess(1L, 1), guess(2L, 2)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(99L);
            c.setCreatedAt(OffsetDateTime.parse("2026-07-30T12:00:00Z"));
            return c;
        });
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice", "https://a")));

        CommentService.CommentDto dto = service.createComment("u1", day, "  great picks  ");

        assertEquals(99L, dto.id());
        assertEquals("great picks", dto.body());
        assertEquals("Alice", dto.author().personaName());
        assertEquals("https://a", dto.author().avatar());
        assertEquals(ReactionType.values().length, dto.reactions().size());
        verify(cacheEvictor).evictCommentsForDay(day);
    }

    @Test
    void listComments_returnsEmptyWhenNone() {
        when(commentRepository.findByGameDateOrderByCreatedAtDesc(eq(day), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(service.listComments(day, null).isEmpty());
        verify(commentReactionRepository, never()).countByCommentIds(any());
    }

    @Test
    void listComments_attachesAuthorAndViewerReactions() {
        Comment comment = comment(7L, "u1", "hello");
        when(commentRepository.findByGameDateOrderByCreatedAtDesc(eq(day), any(Pageable.class)))
                .thenReturn(List.of(comment));

        CommentReactionRepository.ReactionCountRow countRow = mock(CommentReactionRepository.ReactionCountRow.class);
        when(countRow.getCommentId()).thenReturn(7L);
        when(countRow.getReactionType()).thenReturn(ReactionType.THUMBS_UP);
        when(countRow.getReactionCount()).thenReturn(3L);
        when(commentReactionRepository.countByCommentIds(List.of(7L))).thenReturn(List.of(countRow));

        CommentReaction viewerReaction = new CommentReaction();
        viewerReaction.setComment(comment);
        viewerReaction.setSteamId("viewer");
        viewerReaction.setReactionType(ReactionType.THUMBS_UP);
        when(commentReactionRepository.findByComment_IdInAndSteamId(List.of(7L), "viewer"))
                .thenReturn(List.of(viewerReaction));

        when(userRepository.findAllById(any())).thenReturn(List.of(user("u1", "Alice", "https://a")));

        List<CommentService.CommentDto> result = service.listComments(day, "viewer");

        assertEquals(1, result.size());
        CommentService.CommentDto dto = result.getFirst();
        assertEquals("hello", dto.body());
        assertEquals("Alice", dto.author().personaName());
        verify(commentReactionRepository).findByComment_IdInAndSteamId(List.of(7L), "viewer");
        verify(commentReactionRepository, never()).findByComment_IdIn(any());

        CommentService.ReactionDto thumbs = dto.reactions().stream()
                .filter(r -> r.reactionType().equals("THUMBS_UP"))
                .findFirst()
                .orElseThrow();
        assertEquals(3L, thumbs.count());
        assertTrue(thumbs.reactedByViewer());

        CommentService.ReactionDto hug = dto.reactions().stream()
                .filter(r -> r.reactionType().equals("HUG"))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, hug.count());
        assertFalse(hug.reactedByViewer());
    }

    @Test
    void toggleReaction_throwsWhenCommentMissing() {
        when(commentRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        ReviewGameException ex = assertThrows(ReviewGameException.class,
                () -> service.toggleReaction(42L, "u1", ReactionType.HUG));

        assertEquals(404, ex.getStatusCode());
        assertEquals("comment_not_found", ex.getMessage());
    }

    @Test
    void toggleReaction_deletesExistingReaction() {
        Comment comment = comment(7L, "u1", "hello");
        CommentReaction existing = new CommentReaction(3L, comment, "viewer", ReactionType.LAUGH_CRYING, OffsetDateTime.now());
        when(commentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(comment));
        when(commentReactionRepository.findByComment_IdAndSteamIdAndReactionType(
                7L, "viewer", ReactionType.LAUGH_CRYING)).thenReturn(Optional.of(existing));
        when(commentReactionRepository.countByCommentIds(List.of(7L))).thenReturn(List.of());
        when(commentReactionRepository.findByComment_IdInAndSteamId(List.of(7L), "viewer")).thenReturn(List.of());

        List<CommentService.ReactionDto> reactions =
                service.toggleReaction(7L, "viewer", ReactionType.LAUGH_CRYING);

        verify(commentReactionRepository).delete(existing);
        verify(commentReactionRepository, never()).insertIgnoreConflict(any(), any(), any(), any());
        verify(cacheEvictor).evictCommentsForDay(day);
        assertEquals(ReactionType.values().length, reactions.size());
    }

    @Test
    void toggleReaction_createsMissingReaction() {
        Comment comment = comment(7L, "u1", "hello");
        when(commentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(comment));
        when(commentReactionRepository.findByComment_IdAndSteamIdAndReactionType(
                7L, "viewer", ReactionType.HUG)).thenReturn(Optional.empty());
        when(commentReactionRepository.insertIgnoreConflict(eq(7L), eq("viewer"), eq("HUG"), any()))
                .thenReturn(1);
        when(commentReactionRepository.countByCommentIds(List.of(7L))).thenReturn(List.of());
        when(commentReactionRepository.findByComment_IdInAndSteamId(List.of(7L), "viewer")).thenReturn(List.of());

        service.toggleReaction(7L, "viewer", ReactionType.HUG);

        verify(commentReactionRepository).insertIgnoreConflict(eq(7L), eq("viewer"), eq("HUG"), any());
        verify(cacheEvictor).evictCommentsForDay(day);
    }

    @Test
    void toggleReaction_ignoresConflictWhenInsertReturnsZero() {
        // Production path uses INSERT ... ON CONFLICT DO NOTHING (no aborting exception).
        // A full PostgreSQL conflict integration test is skipped: this repo has no
        // Testcontainers/@SpringBootTest harness for comment reactions.
        Comment comment = comment(7L, "u1", "hello");
        when(commentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(comment));
        when(commentReactionRepository.findByComment_IdAndSteamIdAndReactionType(
                7L, "viewer", ReactionType.HUG)).thenReturn(Optional.empty());
        when(commentReactionRepository.insertIgnoreConflict(eq(7L), eq("viewer"), eq("HUG"), any()))
                .thenReturn(0);
        when(commentReactionRepository.countByCommentIds(List.of(7L))).thenReturn(List.of());
        when(commentReactionRepository.findByComment_IdInAndSteamId(List.of(7L), "viewer"))
                .thenReturn(List.of(new CommentReaction(9L, comment, "viewer", ReactionType.HUG, OffsetDateTime.now())));

        List<CommentService.ReactionDto> reactions =
                assertDoesNotThrow(() -> service.toggleReaction(7L, "viewer", ReactionType.HUG));

        verify(commentReactionRepository).insertIgnoreConflict(eq(7L), eq("viewer"), eq("HUG"), any());
        verify(cacheEvictor).evictCommentsForDay(day);
        CommentService.ReactionDto hug = reactions.stream()
                .filter(r -> r.reactionType().equals("HUG"))
                .findFirst()
                .orElseThrow();
        assertTrue(hug.reactedByViewer());
    }

    private static ReviewGamePick pick(Long id, Long appId) {
        return new ReviewGamePick(id, LocalDate.of(2026, 7, 30), appId, OffsetDateTime.now());
    }

    private Guess guess(Long id, int roundIndex) {
        return new Guess(id, "u1", day, roundIndex, 10L + roundIndex, "1-100", "1-100", 5, OffsetDateTime.now());
    }

    private Comment comment(Long id, String steamId, String body) {
        Comment c = new Comment();
        c.setId(id);
        c.setSteamId(steamId);
        c.setGameDate(day);
        c.setBody(body);
        c.setCreatedAt(OffsetDateTime.parse("2026-07-30T12:00:00Z"));
        return c;
    }

    private static User user(String steamId, String personaName, String avatar) {
        User u = new User();
        u.setSteamId(steamId);
        u.setPersonaName(personaName);
        u.setAvatar(avatar);
        return u;
    }
}
