package org.steam5.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    /**
     * One named cache with its TTL and size. The table in {@link #cacheManager()} is the single
     * source of truth; registering a cache in a second list is what used to drift.
     */
    private static CaffeineCache cache(final String name, final Duration ttl, final long maximumSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .build());
    }

    @Bean
    public CacheManager cacheManager() {
        final SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                cache("one-hour", Duration.ofHours(1), 500),
                cache("one-day", Duration.ofDays(1), 500),
                cache("review-game", Duration.ofDays(1), 500),
                cache("stats-long", Duration.ofDays(7), 500),
                cache("stats-daily", Duration.ofDays(1), 500),
                cache("stats-hourly", Duration.ofHours(1), 500),
                cache("stats-short", Duration.ofMinutes(5), 500),
                cache("leaderboard-live", Duration.ofSeconds(60), 200),
                cache("leaderboard-static", Duration.ofMinutes(10), 200),
                cache("cache-stats", Duration.ofMinutes(1), 5),
                cache("season-current-response", Duration.ofMinutes(15), 50),
                cache("season-list-response", Duration.ofMinutes(30), 200),
                cache("season-awards-response", Duration.ofHours(1), 200),
                cache("season-detail-response", Duration.ofMinutes(30), 200),
                cache("season-awards", Duration.ofHours(1), 200),
                cache("player-awards", Duration.ofHours(1), 500),
                cache("player-spotlights", Duration.ofHours(1), 500),
                cache("comments-for-day", Duration.ofSeconds(60), 500)
        ));
        return cacheManager;
    }
}
