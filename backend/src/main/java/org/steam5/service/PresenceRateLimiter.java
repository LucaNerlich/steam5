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

    public boolean tryAcquireTicket(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return increment(ticketByIp, ip) <= properties.getTicketRateLimitPerMinute();
    }

    public boolean tryAcquireTicketForUser(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(ticketByUser, steamId) <= properties.getTicketRateLimitPerUserPerMinute();
    }

    public boolean tryAcquireHandshake(final String ip) {
        if (ip == null || ip.isBlank()) return true;
        return increment(handshakeByIp, ip) <= properties.getHandshakeRateLimitPerMinute();
    }

    private static int increment(final Cache<String, AtomicInteger> cache, final String key) {
        return cache.get(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
