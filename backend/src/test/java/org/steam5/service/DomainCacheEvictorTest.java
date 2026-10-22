package org.steam5.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainCacheEvictorTest {

    @Test
    void evictStatsHourlyKeysLeavesUnrelatedAggregatesCached() {
        final CaffeineCacheManager manager = new CaffeineCacheManager("stats-hourly");
        manager.setCaffeine(Caffeine.newBuilder());
        final Cache cache = manager.getCache("stats-hourly");
        cache.put("hardest-games-10", "hardest");
        cache.put("perfect-days", "perfect");
        cache.put("stats-user-labels-weekly", "weekly");

        new DomainCacheEvictor(manager).evictStatsHourlyKeys("hardest-games-", "perfect-days");

        assertNull(cache.get("hardest-games-10"));
        assertNull(cache.get("perfect-days"));
        assertEquals("weekly", cache.get("stats-user-labels-weekly").get());
    }
}
