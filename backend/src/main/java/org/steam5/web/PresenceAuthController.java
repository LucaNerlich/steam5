package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.security.CurrentUser;
import org.steam5.service.PresenceMetrics;
import org.steam5.service.PresenceRateLimiter;
import org.steam5.service.WsTicketService;
import org.steam5.web.ws.PresenceHandshakeInterceptor;

import java.util.Map;

/**
 * Exchanges an authenticated bearer token for a short-lived WebSocket
 * handshake ticket. Browsers cannot attach {@code Authorization} headers
 * to native WS handshakes, so clients call {@code POST /api/ws/ticket}
 * and then include the returned ticket as a query parameter on the WS URL.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ws")
public class PresenceAuthController {

    private final WsTicketService wsTicketService;
    private final PresenceRateLimiter presenceRateLimiter;
    private final PresenceMetrics presenceMetrics;

    /**
     * Issues a short-lived WebSocket handshake ticket for an authenticated user and scope.
     *
     * @param steamId the authenticated user's identifier
     * @param body    the optional request body containing the required {@code scopeKey}
     * @return a successful ticket response, or an error response for unauthenticated requests,
     *         invalid scope keys, or exceeded rate limits
     */
    @PostMapping("/ticket")
    public ResponseEntity<?> issueTicket(@CurrentUser final String steamId,
                                         @RequestBody(required = false) final Map<String, String> body) {
        if (steamId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }

        final String scopeKey = body == null ? null : body.get("scopeKey");
        if (scopeKey == null || scopeKey.isBlank()
                || !PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN.matcher(scopeKey).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_scope_key"));
        }

        if (!presenceRateLimiter.tryAcquireTicketForUser(steamId)) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
        }

        final String ticket = wsTicketService.issueTicket(steamId, scopeKey);
        presenceMetrics.recordTicketIssued();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("ticket", ticket));
    }
}
