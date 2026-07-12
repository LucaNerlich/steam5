package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.security.CurrentUser;
import org.steam5.service.WsTicketService;

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

    @PostMapping("/ticket")
    public ResponseEntity<?> issueTicket(@CurrentUser String steamId) {
        if (steamId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        final String ticket = wsTicketService.issueTicket(steamId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("ticket", ticket));
    }
}
