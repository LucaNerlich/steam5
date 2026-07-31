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

    public boolean tryAcquireComment(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(commentsByUser, steamId) <= COMMENT_LIMIT_PER_MINUTE;
    }

    public boolean tryAcquireReaction(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return increment(reactionsByUser, steamId) <= REACTION_LIMIT_PER_MINUTE;
    }

    private static int increment(final Cache<String, AtomicInteger> cache, final String key) {
        return cache.get(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
