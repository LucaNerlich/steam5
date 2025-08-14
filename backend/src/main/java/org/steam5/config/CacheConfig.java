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
                        .build()
        );

        final CaffeineCache cacheOneDay = new CaffeineCache(
                "one-day",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .build()
        );

        final CaffeineCache cacheReviewGame = new CaffeineCache(
                "review-game",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .build()
        );

        final SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                cacheOneHour,
                cacheOneDay,
                cacheReviewGame
        ));
        return cacheManager;
    }
}
