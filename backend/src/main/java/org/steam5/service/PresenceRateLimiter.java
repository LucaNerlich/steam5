package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.steam5.config.PresenceProperties;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for presence ticket issuance and WebSocket handshakes.
 */
@Service
@RequiredArgsConstructor
public class PresenceRateLimiter {

    private final PresenceProperties properties;

    private final Cache<String, AtomicInteger> ticketByIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    private final Cache<String, AtomicInteger> ticketByUser = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    private final Cache<String, AtomicInteger> handshakeByIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    /**
     * Determines whether a presence ticket request from an IP address is within the configured rate limit.
     *
     * @param ip the requesting IP address; blank or null values are allowed without rate limiting
     * @return {@code true} if the request is allowed, {@code false} otherwise
     */
    public boolean tryAcquireTicket(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return increment(ticketByIp, ip) <= properties.getTicketRateLimitPerMinute();
    }

    /**
     * Determines whether a presence ticket request is allowed for a user.
     *
     * @param steamId the Steam user identifier used for rate limiting
     * @return {@code true} if the request is within the configured per-user limit or the identifier is blank, {@code false} otherwise
     */
    public boolean tryAcquireTicketForUser(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(ticketByUser, steamId) <= properties.getTicketRateLimitPerUserPerMinute();
    }

    /**
     * Determines whether a WebSocket handshake attempt from an IP address is within the configured rate limit.
     *
     * @param ip the source IP address
     * @return {@code true} if the attempt is allowed or the IP address is blank, {@code false} otherwise
     */
    public boolean tryAcquireHandshake(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return increment(handshakeByIp, ip) <= properties.getHandshakeRateLimitPerMinute();
    }

    /**
     * Increments the counter associated with a key and returns its updated value.
     *
     * @param cache the cache containing counters by key
     * @param key   the counter key
     * @return the incremented counter value
     */
    private static int increment(final Cache<String, AtomicInteger> cache, final String key) {
        return cache.get(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
