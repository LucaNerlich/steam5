package org.steam5.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReactionType;
import org.steam5.security.CurrentUser;
import org.steam5.service.CommentRateLimiter;
import org.steam5.service.CommentService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game/comments")
public class CommentController {

    private static final int MAX_BODY_LENGTH = 1000;
    // Viewer-specific reactedByViewer flags — keep out of shared/CDN caches.
    private static final String CACHE_LIVE = "private, max-age=30, must-revalidate";
    // Past days are write-closed for users, but moderators can still soft-archive.
    // Keep a short revalidating TTL so archival is not stuck behind a year-long immutable cache.
    private static final String CACHE_HISTORICAL = "private, max-age=300, must-revalidate";
    private static final String CACHE_NO_STORE = "private, no-store";

    private final CommentService commentService;
    private final CommentRateLimiter commentRateLimiter;

    @GetMapping("/{date}")
    public ResponseEntity<?> listComments(
            @PathVariable("date") final String date,
            @CurrentUser final String steamId) {
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return rejected("GET /api/review-game/comments/{date}", "invalid_date", 400);
        }
        // Authenticated payloads include reactedByViewer — never long-cache them.
        final String cc;
        if (steamId != null && !steamId.isBlank()) {
            cc = CACHE_NO_STORE;
        } else {
            final boolean isToday = day.equals(GameDate.todayUtc());
            cc = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
        }
        return ResponseEntity.ok()
                .header("Cache-Control", cc)
                .body(commentService.listComments(day, steamId));
    }

    @PostMapping("/{date}")
    public ResponseEntity<?> createComment(
            @PathVariable("date") final String date,
            @CurrentUser final String steamId,
            @RequestBody(required = false) final CreateCommentRequest req) {
        if (steamId == null) {
            return rejected("POST /api/review-game/comments/{date}", "unauthenticated", 401);
        }
        if (req == null || req.body() == null || req.body().isBlank()) {
            return rejected("POST /api/review-game/comments/{date}", "invalid_body", 400);
        }
        final String body = req.body().trim();
        if (body.length() > MAX_BODY_LENGTH) {
            return rejected("POST /api/review-game/comments/{date}", "body_too_long", 400);
        }
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return rejected("POST /api/review-game/comments/{date}", "invalid_date", 400);
        }
        // Reject non-current game days before consuming a rate-limit token.
        if (!day.equals(GameDate.todayUtc())) {
            return rejected("POST /api/review-game/comments/{date}", "not_current_game_day", 400);
        }
        if (!commentRateLimiter.tryAcquireComment(steamId)) {
            return rejected("POST /api/review-game/comments/{date}", "rate_limit_exceeded", 429);
        }
        final CommentService.CommentDto created = commentService.createComment(steamId, day, body);
        return ResponseEntity.status(201)
                .header("Cache-Control", CACHE_NO_STORE)
                .body(created);
    }

    @PostMapping("/{commentId}/reactions")
    public ResponseEntity<?> toggleReaction(
            @PathVariable("commentId") final Long commentId,
            @CurrentUser final String steamId,
            @RequestBody(required = false) final ReactionRequest req) {
        if (steamId == null) {
            return rejected("POST /api/review-game/comments/{commentId}/reactions", "unauthenticated", 401);
        }
        if (req == null || req.reactionType() == null || req.reactionType().isBlank()) {
            return rejected("POST /api/review-game/comments/{commentId}/reactions", "invalid_reaction_type", 400);
        }
        final ReactionType reactionType;
        try {
            reactionType = ReactionType.valueOf(req.reactionType().trim());
        } catch (IllegalArgumentException ex) {
            return rejected("POST /api/review-game/comments/{commentId}/reactions", "invalid_reaction_type", 400);
        }
        if (!commentRateLimiter.tryAcquireReaction(steamId)) {
            return rejected("POST /api/review-game/comments/{commentId}/reactions", "rate_limit_exceeded", 429);
        }
        final List<CommentService.ReactionDto> reactions =
                commentService.toggleReaction(commentId, steamId, reactionType);
        return ResponseEntity.ok()
                .header("Cache-Control", CACHE_NO_STORE)
                .body(reactions);
    }

    /**
     * Soft-archives a comment. Only the hardcoded comment moderator may call this.
     */
    @PostMapping("/{commentId}/archive")
    public ResponseEntity<?> archiveComment(
            @PathVariable("commentId") final Long commentId,
            @CurrentUser final String steamId) {
        if (steamId == null) {
            return rejected("POST /api/review-game/comments/{commentId}/archive", "unauthenticated", 401);
        }
        commentService.archiveComment(commentId, steamId);
        return ResponseEntity.ok()
                .header("Cache-Control", CACHE_NO_STORE)
                .body(Map.of("ok", true));
    }

    /**
     * Structured rejection log for auth/validation/rate-limit branches.
     * Omits request bodies, tokens, and raw Steam IDs.
     */
    private static ResponseEntity<Map<String, String>> rejected(
            final String endpoint,
            final String error,
            final int status) {
        final String correlationId = MDC.get("correlationId");
        log.warn("Comment API rejected: endpoint={} error={} status={} correlationId={}",
                endpoint, error, status, correlationId != null ? correlationId : "n/a");
        return ResponseEntity.status(status).body(Map.of("error", error));
    }

    public record CreateCommentRequest(String body) {
    }

    public record ReactionRequest(String reactionType) {
    }
}
