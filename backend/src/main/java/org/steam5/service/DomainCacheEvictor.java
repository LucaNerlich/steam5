package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Single owner of the domain cache topology: which Caffeine caches exist and which
 * entries must be evicted when underlying data changes. Services call the named
 * operations below rather than reaching for cache keys directly, so that adding a
 * cache or renaming a key is a one-file change.
 *
 * <p>Cache names mirror the beans declared in
 * {@link org.steam5.config.CacheConfig}.</p>
 */
@Component
@RequiredArgsConstructor
public class DomainCacheEvictor {

    static final String REVIEW_GAME = "review-game";
    static final String ONE_DAY = "one-day";

    private final CacheManager cacheManager;

    /**
     * Drop all cached review-game state. Call after the day's picks are (re)generated,
     * since the cached picks/state are no longer current.
     */
    public void evictReviewGameState() {
        clear(REVIEW_GAME);
    }

    /**
     * Drop cached data derived from a single app's details. Call after an app's
     * details are upserted: the per-app {@code one-day} entry is stale, and the
     * review-game responses embed app details so they must be dropped too.
     */
    public void evictAppDetail(final Long appId) {
        final Cache oneDay = cacheManager.getCache(ONE_DAY);
        if (oneDay != null) {
            oneDay.evict(appId);
        }
        clear(REVIEW_GAME);
    }

    private void clear(final String cacheName) {
        final Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
