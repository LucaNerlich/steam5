package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
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
 * <p>Tickets are bound to a {@code scopeKey} at issue time and consumed on
 * first validation to prevent reuse across scopes.</p>
 */
@Slf4j
@Service
public class WsTicketService {

    private static final long TICKET_TTL_SECONDS = 30L;
    private static final long TICKET_MAX_SIZE = 500L;

    record TicketEntry(String steamId, String scopeKey) {
    }

    private final Cache<String, TicketEntry> tickets = Caffeine.newBuilder()
            .expireAfterWrite(TICKET_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(TICKET_MAX_SIZE)
            .build();

    public String issueTicket(final String steamId, final String scopeKey) {
        final String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new TicketEntry(steamId, scopeKey));
        return ticket;
    }

    /**
     * Validates and consumes a ticket. Returns the steamId when the ticket is
     * valid for the requested scope, otherwise {@code null}.
     */
    public String validateTicket(final String ticket, final String scopeKey) {
        if (ticket == null || scopeKey == null || scopeKey.isBlank()) return null;
        final TicketEntry entry = tickets.asMap().remove(ticket);
        if (entry == null) {
            log.debug("WS ticket validation failed — ticket not found or expired");
            return null;
        }
        if (!scopeKey.equals(entry.scopeKey())) {
            log.debug("WS ticket validation failed — scope mismatch");
            return null;
        }
        log.debug("WS ticket validated for steamId={}", entry.steamId());
        return entry.steamId();
    }
}
