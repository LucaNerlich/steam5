package org.steam5.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        final CaffeineCache cacheOneHour = new CaffeineCache(
                "one-hour",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheOneDay = new CaffeineCache(
                "one-day",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheReviewGame = new CaffeineCache(
                "review-game",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheStatsLong = new CaffeineCache(
                "stats-long",
                Caffeine.newBuilder()
                        .expireAfterWrite(7, TimeUnit.DAYS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheStatsHourly = new CaffeineCache(
                "stats-hourly",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheStatsShort = new CaffeineCache(
                "stats-short",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final CaffeineCache cacheStatsEndpoint = new CaffeineCache(
                "cache-stats",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .maximumSize(5)
                        .recordStats()
                        .build()
        );

        final CaffeineCache seasonCurrent = new CaffeineCache(
                "season-current-response",
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .recordStats()
                        .build()
        );

        final CaffeineCache seasonList = new CaffeineCache(
                "season-list-response",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .recordStats()
                        .build()
        );

        final CaffeineCache seasonAwardsResponse = new CaffeineCache(
                "season-awards-response",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(200)
                        .recordStats()
                        .build()
        );

        final CaffeineCache seasonDetailResponse = new CaffeineCache(
                "season-detail-response",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .recordStats()
                        .build()
        );

        final CaffeineCache seasonAwards = new CaffeineCache(
                "season-awards",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(200)
                        .recordStats()
                        .build()
        );

        final CaffeineCache playerAwards = new CaffeineCache(
                "player-awards",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        final SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                cacheOneHour,
                cacheOneDay,
                cacheReviewGame,
                cacheStatsLong,
                cacheStatsHourly,
                cacheStatsShort,
                cacheStatsEndpoint,
                seasonCurrent,
                seasonList,
                seasonAwardsResponse,
                seasonDetailResponse,
                seasonAwards,
                playerAwards
        ));
        return cacheManager;
    }
}
