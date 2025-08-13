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
        final CaffeineCache reviewGameStateTodayPicks = new CaffeineCache(
                "reviewGameStateTodayPicks",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build()
        );

        final CaffeineCache reviewGameStateTodayDetails = new CaffeineCache(
                "reviewGameStateTodayDetails",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .build()
        );

        final SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                reviewGameStateTodayPicks,
                reviewGameStateTodayDetails
        ));
        return cacheManager;
    }
}
