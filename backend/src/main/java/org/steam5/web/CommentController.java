package org.steam5.web;

import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game/comments")
public class CommentController {

    private static final int MAX_BODY_LENGTH = 1000;
    // Viewer-specific reactedByViewer flags — keep out of shared/CDN caches.
    private static final String CACHE_LIVE = "private, max-age=30, must-revalidate";
    private static final String CACHE_HISTORICAL = "private, max-age=300";

    private final CommentService commentService;
    private final CommentRateLimiter commentRateLimiter;

    /**
     * Lists comments for the specified date.
     *
     * @param date   the date in ISO-8601 format
     * @param steamId the authenticated viewer's Steam ID, if available
     * @return       the comments for the date, or a bad-request response when the date is invalid
     */
    @GetMapping("/{date}")
    public ResponseEntity<?> listComments(
            @PathVariable("date") final String date,
            @CurrentUser final String steamId) {
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_date"));
        }
        final boolean isToday = day.equals(GameDate.todayUtc());
        final String cc = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
        return ResponseEntity.ok()
                .header("Cache-Control", cc)
                .body(commentService.listComments(day, steamId));
    }

    /**
     * Creates a comment for the specified date.
     *
     * @param date the comment date in ISO-8601 format
     * @param steamId the authenticated Steam user identifier
     * @param req the request containing the comment body
     * @return the created comment, or an error response for unauthenticated, invalid,
     *         rate-limited, or invalid-date requests
     */
    @PostMapping("/{date}")
    public ResponseEntity<?> createComment(
            @PathVariable("date") final String date,
            @CurrentUser final String steamId,
            @RequestBody(required = false) final CreateCommentRequest req) {
        if (steamId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        if (req == null || req.body() == null || req.body().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_body"));
        }
        final String body = req.body().trim();
        if (body.length() > MAX_BODY_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "body_too_long"));
        }
        if (!commentRateLimiter.tryAcquireComment(steamId)) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
        }
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_date"));
        }
        final CommentService.CommentDto created = commentService.createComment(steamId, day, body);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(created);
    }

    /**
     * Toggles a reaction on a comment for an authenticated user.
     *
     * @param commentId the identifier of the comment
     * @param req       the requested reaction type
     * @return the updated reactions, or an error response for invalid authentication,
     *         reaction types, or rate limits
     */
    @PostMapping("/{commentId}/reactions")
    public ResponseEntity<?> toggleReaction(
            @PathVariable("commentId") final Long commentId,
            @CurrentUser final String steamId,
            @RequestBody(required = false) final ReactionRequest req) {
        if (steamId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        if (req == null || req.reactionType() == null || req.reactionType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_reaction_type"));
        }
        final ReactionType reactionType;
        try {
            reactionType = ReactionType.valueOf(req.reactionType().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_reaction_type"));
        }
        if (!commentRateLimiter.tryAcquireReaction(steamId)) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
        }
        final List<CommentService.ReactionDto> reactions =
                commentService.toggleReaction(commentId, steamId, reactionType);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(reactions);
    }

    public record CreateCommentRequest(String body) {
    }

    public record ReactionRequest(String reactionType) {
    }
}
