package org.steam5.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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
    static final String LEADERBOARD_STATIC = "leaderboard-static";
    static final String STATS_HOURLY = "stats-hourly";
    static final String COMMENTS_FOR_DAY = "comments-for-day";

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
        final org.springframework.cache.Cache oneDay = cacheManager.getCache(ONE_DAY);
        if (oneDay != null) {
            oneDay.evict(appId);
        }
        clear(REVIEW_GAME);
    }

    /**
     * Drop only the per-app {@code one-day} cache entry for a single app, without clearing
     * the shared {@code review-game} cache. Used by bulk loops (e.g. the full-run blurhash
     * job) that clear {@code review-game} once up front but still need each processed app's
     * cached detail response invalidated so freshly encoded screenshot blur data is served
     * immediately rather than up to 24h later.
     */
    public void evictAppDetailEntry(final Long appId) {
        final org.springframework.cache.Cache oneDay = cacheManager.getCache(ONE_DAY);
        if (oneDay != null) {
            oneDay.evict(appId);
        }
    }

    /**
     * Drop cached leaderboard responses (all-time, monthly, weekly-floating, season). Call
     * after a leaderboard materialized view refresh, since the cached entries would otherwise
     * keep serving pre-refresh data for up to the cache's 10-minute TTL.
     */
    public void evictLeaderboardStatic() {
        clear(LEADERBOARD_STATIC);
    }

    /**
     * Clears cached hourly statistics, including hardest-games rankings and related aggregates.
     */
    public void evictStatsHourly() {
        clear(STATS_HOURLY);
    }

    /**
     * Clears cached comment-list entries for the given day only.
     * Keys match {@code CommentService.listComments}: {@code gameDate.toString() + ':' + viewer}.
     *
     * @param day the day whose comment cache entries must be dropped
     */
    public void evictCommentsForDay(final LocalDate day) {
        final org.springframework.cache.Cache cache = cacheManager.getCache(COMMENTS_FOR_DAY);
        if (cache == null) {
            return;
        }
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            cache.clear();
            return;
        }
        final String prefix = day.toString() + ":";
        final Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        for (final Object key : nativeCache.asMap().keySet()) {
            if (key instanceof String s && s.startsWith(prefix)) {
                caffeineCache.evict(key);
            }
        }
    }

    /**
     * Clears the named cache when it is available.
     *
     * @param cacheName the name of the cache to clear
     */
    private void clear(final String cacheName) {
        final org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
