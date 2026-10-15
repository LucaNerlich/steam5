package org.steam5.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request counter keyed by an arbitrary identity (client IP, steamId, …).
 *
 * <p>An entry expires {@value #WINDOW_SECONDS} seconds after the key's first request in the
 * window, and the cache is bounded so an identity-distributed flood cannot grow it without limit.
 * Every request limiter in the application — the per-IP servlet filters and the presence/comment
 * limiters — uses this one type, so the window policy, the memory bound and the count-then-compare
 * idiom cannot drift between them.</p>
 */
public class FixedWindowCounter {

    /** Window length in seconds, anchored at the key's first request. */
    public static final int WINDOW_SECONDS = 60;

    /** Hard cap on tracked identities per counter. */
    public static final long MAX_KEYS = 10_000L;

    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
            .maximumSize(MAX_KEYS)
            .build();

    private final int limit;

    /**
     * @param limitPerWindow how many requests a single key may make per window
     */
    public FixedWindowCounter(final int limitPerWindow) {
        this.limit = limitPerWindow;
    }

    /**
     * Counts one request for {@code key}.
     *
     * @param key the identity to count against; must not be null
     * @return {@code true} while the key is within the window's limit, {@code false} once exceeded
     */
    public boolean tryAcquire(final String key) {
        return counts.get(key, ignored -> new AtomicInteger(0)).incrementAndGet() <= limit;
    }
}
