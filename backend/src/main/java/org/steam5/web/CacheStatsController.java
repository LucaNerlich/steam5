package org.steam5.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cache")
public class CacheStatsController {

    private final CacheManager cacheManager;

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Cacheable(value = "cache-stats", key = "'stats'", unless = "#result == null || #result.body == null")
    public ResponseEntity<CacheStatsPayload> cacheStats() {
        final List<CacheStatsResponse> caches = cacheManager.getCacheNames().stream()
                .sorted(Comparator.naturalOrder())
                .map(this::describeCache)
                .toList();

        final long totalEstimatedSize = caches.stream()
                .map(CacheStatsResponse::estimatedSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        final CacheStatsPayload payload = new CacheStatsPayload(
                caches.size(),
                totalEstimatedSize,
                caches
        );

        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=0, no-store")
                .body(payload);
    }

    private CacheStatsResponse describeCache(final String cacheName) {
        final org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            return new CacheStatsResponse(
                    cacheName,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        final Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        final CacheStats stats = nativeCache.stats();
        final Long maximumSize = nativeCache.policy().eviction()
                .map(eviction -> {
                    try {
                        return eviction.getMaximum();
                    } catch (UnsupportedOperationException ignored) {
                        return null;
                    }
                })
                .orElse(null);

        return new CacheStatsResponse(
                cacheName,
                true,
                nativeCache.estimatedSize(),
                maximumSize,
                stats.hitCount(),
                stats.missCount(),
                stats.requestCount(),
                stats.hitRate(),
                stats.evictionCount(),
                stats.loadSuccessCount(),
                stats.loadFailureCount()
        );
    }

    public record CacheStatsPayload(
            int cacheCount,
            long totalEstimatedSize,
            List<CacheStatsResponse> caches
    ) {
    }

    public record CacheStatsResponse(
            String name,
            boolean supportsStats,
            Long estimatedSize,
            Long maximumSize,
            Long hitCount,
            Long missCount,
            Long requestCount,
            Double hitRate,
            Long evictionCount,
            Long loadSuccessCount,
            Long loadFailureCount
    ) {
    }
}

