package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caps anonymous (unauthenticated) guesses at one attempt per (client IP, appId)
 * for a still-live daily pick.
 *
 * <p>The anonymous guess endpoint reveals the true answer regardless of whether
 * the guess was correct, and (unlike the authenticated path) has no persisted,
 * per-user one-guess-per-round constraint. Without a limiter, a single caller
 * could query the same live appId to learn the answer for free, then replay it
 * via the authenticated endpoint for a guaranteed maximum score. This mirrors
 * the authenticated path's one-guess-per-round guarantee using the caller's IP
 * as the identity proxy, since anonymous callers have none.</p>
 */
@Service
public class AnonymousGuessLimiter {

    private final Cache<String, Boolean> claimed = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(26)) // covers a full UTC day plus buffer
            .maximumSize(100_000)
            .build();

    /**
     * Attempts to claim the first anonymous guess for a given client IP and appId.
     *
     * @param ip    the caller's apparent IP address
     * @param appId the app being guessed
     * @return {@code true} if this is the first claim (the caller may see the reveal),
     * {@code false} if this IP already guessed this appId
     */
    public boolean tryClaim(final String ip, final Long appId) {
        final String key = ip + ':' + appId;
        return claimed.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }
}
