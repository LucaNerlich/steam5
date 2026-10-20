package org.steam5.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsServiceCacheTest {

    @Test
    void allTimeAchievementsUseTheirOwnDailyCache() throws Exception {
        // The global aggregate must not share the client-keyed one-day detail cache; app-detail
        // churn evicted it and forced a full-history recompute.
        final Cacheable cacheable = StatisticsService.class
                .getMethod("getUserAchievements")
                .getAnnotation(Cacheable.class);
        assertEquals("stats-daily", cacheable.value()[0]);
    }
}
