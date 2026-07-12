package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Issues and validates short-lived, one-time tickets used to authenticate
 * WebSocket handshakes. Browsers cannot attach the {@code Authorization}
 * header to native WS handshakes, so authenticated clients first exchange
 * their bearer token for a ticket via {@code POST /api/ws/ticket} and then
 * include that ticket as a query parameter on the WS URL.
 *
 * <p>Tickets are stored in an in-memory Caffeine cache keyed by the opaque
 * ticket string. Entries expire 30 seconds after issue and each ticket is
 * consumed on first validation to prevent reuse.</p>
 */
@Service
public class WsTicketService {

    private static final long TICKET_TTL_SECONDS = 30L;
    private static final long TICKET_MAX_SIZE = 500L;

    private final Cache<String, String> tickets = Caffeine.newBuilder()
            .expireAfterWrite(TICKET_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(TICKET_MAX_SIZE)
            .build();

    public String issueTicket(final String steamId) {
        final String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, steamId);
        return ticket;
    }

    public String validateTicket(final String ticket) {
        if (ticket == null) return null;
        final String steamId = tickets.getIfPresent(ticket);
        if (steamId != null) {
            tickets.invalidate(ticket);
        }
        return steamId;
    }
}
