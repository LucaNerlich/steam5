package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.CommentModerator;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReactionType;
import org.steam5.service.CommentRateLimiter;
import org.steam5.service.CommentService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentControllerTest {

    private CommentService commentService;
    private CommentRateLimiter commentRateLimiter;
    private CommentController controller;

    @BeforeEach
    void setUp() {
        commentService = mock(CommentService.class);
        commentRateLimiter = mock(CommentRateLimiter.class);
        controller = new CommentController(commentService, commentRateLimiter);
        when(commentRateLimiter.tryAcquireComment(any())).thenReturn(true);
        when(commentRateLimiter.tryAcquireReaction(any())).thenReturn(true);
    }

    @Test
    void listComments_usesLiveCacheControlForTodayAnonymous() {
        LocalDate today = GameDate.todayUtc();
        when(commentService.listComments(today, null)).thenReturn(List.of());

        ResponseEntity<?> response = controller.listComments(today.toString(), null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, max-age=30, must-revalidate",
                response.getHeaders().getFirst("Cache-Control"));
        verify(commentService).listComments(today, null);
    }

    @Test
    void listComments_usesHistoricalCacheControlForPastDatesAnonymous() {
        LocalDate past = GameDate.todayUtc().minusDays(3);
        when(commentService.listComments(past, null)).thenReturn(List.of());

        ResponseEntity<?> response = controller.listComments(past.toString(), null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, max-age=300, must-revalidate",
                response.getHeaders().getFirst("Cache-Control"));
        verify(commentService).listComments(past, null);
    }

    @Test
    void listComments_usesNoStoreWhenAuthenticated() {
        LocalDate past = GameDate.todayUtc().minusDays(3);
        when(commentService.listComments(past, "viewer")).thenReturn(List.of());

        ResponseEntity<?> response = controller.listComments(past.toString(), "viewer");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        verify(commentService).listComments(past, "viewer");
    }

    @Test
    void listComments_rejectsMalformedDate() {
        ResponseEntity<?> response = controller.listComments("not-a-date", null);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid_date", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).listComments(any(), any());
    }

    @Test
    void createComment_rejectsMalformedDateWithoutConsumingRateLimit() {
        ResponseEntity<?> response = controller.createComment(
                "not-a-date", "u1", new CommentController.CreateCommentRequest("hi"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid_date", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentRateLimiter, never()).tryAcquireComment(any());
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_rejectsPastGameDayWithoutConsumingRateLimit() {
        String past = GameDate.todayUtc().minusDays(1).toString();

        ResponseEntity<?> response = controller.createComment(
                past, "u1", new CommentController.CreateCommentRequest("hi"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("not_current_game_day", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentRateLimiter, never()).tryAcquireComment(any());
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_returns401WhenUnauthenticated() {
        ResponseEntity<?> response = controller.createComment(
                GameDate.todayUtc().toString(), null, new CommentController.CreateCommentRequest("hi"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("unauthenticated", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_rejectsBlankBody() {
        ResponseEntity<?> response = controller.createComment(
                GameDate.todayUtc().toString(), "u1", new CommentController.CreateCommentRequest("   "));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid_body", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_rejectsBodyOverMaxLength() {
        String tooLong = "x".repeat(1001);

        ResponseEntity<?> response = controller.createComment(
                GameDate.todayUtc().toString(), "u1", new CommentController.CreateCommentRequest(tooLong));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("body_too_long", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_returns429WhenRateLimited() {
        when(commentRateLimiter.tryAcquireComment("u1")).thenReturn(false);
        LocalDate today = GameDate.todayUtc();

        ResponseEntity<?> response = controller.createComment(
                today.toString(), "u1", new CommentController.CreateCommentRequest("hi"));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("rate_limit_exceeded", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    void createComment_returns201AndDelegatesWhenValid() {
        LocalDate today = GameDate.todayUtc();
        CommentService.CommentDto dto = new CommentService.CommentDto(
                1L, "hi", today + "T12:00:00Z",
                new CommentService.AuthorDto("u1", "Alice", null, null),
                List.of()
        );
        when(commentService.createComment(eq("u1"), eq(today), eq("hi"))).thenReturn(dto);

        ResponseEntity<?> response = controller.createComment(
                today.toString(), "u1", new CommentController.CreateCommentRequest("  hi  "));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertSame(dto, response.getBody());
        verify(commentService).createComment(eq("u1"), eq(today), eq("hi"));
    }

    @Test
    void toggleReaction_returns401WhenUnauthenticated() {
        ResponseEntity<?> response = controller.toggleReaction(
                7L, null, new CommentController.ReactionRequest("THUMBS_UP"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("unauthenticated", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).toggleReaction(any(), any(), any());
    }

    @Test
    void toggleReaction_rejectsInvalidReactionType() {
        ResponseEntity<?> response = controller.toggleReaction(
                7L, "u1", new CommentController.ReactionRequest("NOT_A_REACTION"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid_reaction_type", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).toggleReaction(any(), any(), any());
    }

    @Test
    void toggleReaction_returns429WhenRateLimited() {
        when(commentRateLimiter.tryAcquireReaction("u1")).thenReturn(false);

        ResponseEntity<?> response = controller.toggleReaction(
                7L, "u1", new CommentController.ReactionRequest("HUG"));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("rate_limit_exceeded", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).toggleReaction(any(), any(), any());
    }

    @Test
    void toggleReaction_returns200AndDelegatesWhenValid() {
        List<CommentService.ReactionDto> reactions = List.of(
                new CommentService.ReactionDto("HUG", 1L, true)
        );
        when(commentService.toggleReaction(7L, "u1", ReactionType.HUG)).thenReturn(reactions);

        ResponseEntity<?> response = controller.toggleReaction(
                7L, "u1", new CommentController.ReactionRequest("HUG"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertSame(reactions, response.getBody());
    }

    @Test
    void archiveComment_returns401WhenUnauthenticated() {
        ResponseEntity<?> response = controller.archiveComment(7L, null);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("unauthenticated", ((Map<?, ?>) response.getBody()).get("error"));
        verify(commentService, never()).archiveComment(any(), any());
    }

    @Test
    void archiveComment_delegatesWhenAuthenticated() {
        ResponseEntity<?> response = controller.archiveComment(7L, CommentModerator.STEAM_ID);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        verify(commentService).archiveComment(7L, CommentModerator.STEAM_ID);
    }
}
