package org.steam5.web;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheStatsControllerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        final CaffeineCache cache = new CaffeineCache(
                "test-cache",
                Caffeine.newBuilder()
                        .maximumSize(10)
                        .recordStats()
                        .build()
        );
        cache.getNativeCache().put("key", "value");
        cache.getNativeCache().getIfPresent("key");
        cache.getNativeCache().getIfPresent("missing");

        final SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(cache));
        simpleCacheManager.initializeCaches();
        this.cacheManager = simpleCacheManager;
    }

    @Test
    void cacheStats_returnsPayload() {
        final CacheStatsController controller = new CacheStatsController(cacheManager);
        final ResponseEntity<CacheStatsController.CacheStatsPayload> response = controller.cacheStats();

        assertEquals(200, response.getStatusCode().value());
        final CacheStatsController.CacheStatsPayload payload = response.getBody();
        assertNotNull(payload);
        assertEquals(1, payload.cacheCount());
        assertEquals(1, payload.caches().size());

        final CacheStatsController.CacheStatsResponse stats = payload.caches().get(0);
        assertEquals("test-cache", stats.name());
        assertTrue(stats.supportsStats());
        assertNotNull(stats.estimatedSize());
        assertNotNull(stats.requestCount());
    }
}

