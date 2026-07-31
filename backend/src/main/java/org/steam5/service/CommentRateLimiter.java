package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-user rate limiter for comment posts and reaction toggles.
 */
@Service
public class CommentRateLimiter {

    private static final int COMMENT_LIMIT_PER_MINUTE = 5;
    private static final int REACTION_LIMIT_PER_MINUTE = 30;

    private final Cache<String, AtomicInteger> commentsByUser = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    private final Cache<String, AtomicInteger> reactionsByUser = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    /**
     * Determines whether a user may submit another comment within the current minute.
     *
     * @param steamId the user's Steam ID; null or blank IDs bypass the rate limit
     * @return {@code true} if the user is within the five-comment limit, {@code false} otherwise
     */
    public boolean tryAcquireComment(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(commentsByUser, steamId) <= COMMENT_LIMIT_PER_MINUTE;
    }

    /**
     * Determines whether a user may submit another reaction within the current minute.
     *
     * @param steamId the user's Steam ID; null or blank IDs bypass rate limiting
     * @return {@code true} if the user is within the per-minute reaction limit, {@code false} otherwise
     */
    public boolean tryAcquireReaction(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(reactionsByUser, steamId) <= REACTION_LIMIT_PER_MINUTE;
    }

    /**
     * Increments the counter associated with a key in the cache.
     *
     * @param cache the cache containing the counters
     * @param key   the counter key
     * @return the counter value after incrementing
     */
    private static int increment(final Cache<String, AtomicInteger> cache, final String key) {
        return cache.get(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
