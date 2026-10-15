package org.steam5.service;

import org.springframework.stereotype.Service;
import org.steam5.config.PresenceProperties;
import org.steam5.security.FixedWindowCounter;

@Service
public class PresenceRateLimiter {

    private final FixedWindowCounter ticketsByIp;
    private final FixedWindowCounter ticketsByUser;
    private final FixedWindowCounter handshakesByIp;

    /**
     * Builds one fixed-window counter per limit from the configured presence properties.
     *
     * @param properties the presence limits
     */
    public PresenceRateLimiter(final PresenceProperties properties) {
        this.ticketsByIp = new FixedWindowCounter(properties.getTicketRateLimitPerMinute());
        this.ticketsByUser = new FixedWindowCounter(properties.getTicketRateLimitPerUserPerMinute());
        this.handshakesByIp = new FixedWindowCounter(properties.getHandshakeRateLimitPerMinute());
    }

    /**
     * Determines whether a presence ticket request from an IP address is within the configured rate limit.
     *
     * @param ip the requesting IP address; blank or null values are allowed without rate limiting
     * @return {@code true} if the request is allowed, {@code false} otherwise
     */
    public boolean tryAcquireTicket(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return ticketsByIp.tryAcquire(ip);
    }

    /**
     * Determines whether a presence ticket request is allowed for a user.
     *
     * @param steamId the Steam user identifier used for rate limiting
     * @return {@code true} if the request is within the configured per-user limit or the identifier is blank, {@code false} otherwise
     */
    public boolean tryAcquireTicketForUser(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return ticketsByUser.tryAcquire(steamId);
    }

    /**
     * Determines whether a WebSocket handshake attempt from an IP address is within the configured rate limit.
     *
     * @param ip the source IP address
     * @return {@code true} if the attempt is allowed or the IP address is blank, {@code false} otherwise
     */
    public boolean tryAcquireHandshake(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return handshakesByIp.tryAcquire(ip);
    }
}
