package org.steam5.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shared {@code review-game} cache holds the page entries every visitor reads. Nothing
 * reachable from {@code /guess} may mint entries in it (see #340).
 */
class ReviewGameStateCachingTest {

    private ReviewGameStateService service;
    private ReviewGamePickRepository pickRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                mock(GuessRepository.class),
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                pickRepository,
                mock(Scheduler.class),
                new SimpleMeterRegistry());
    }

    @Test
    void reviewCountLookupIsNotCachedUnderAClientSuppliedKey() throws NoSuchMethodException {
        // Anonymous /guess calls getTotalReviewCountForApp with any appId; @Cacheable here minted
        // one entry per id and evicted today-picks/days/answers from the 500-entry page cache.
        final Method method = ReviewGameStateService.class.getMethod("getTotalReviewCountForApp", Long.class);

        assertNull(AnnotationUtils.findAnnotation(method, Cacheable.class));
    }

    @Test
    void alwaysPickUsesTheRecordedAnswerNotTheCurrentSnapshot() {
        final LocalDate day = GameDate.todayUtc().minusDays(1);
        final ReviewGamePick pick = new ReviewGamePick(1L, day, 42L, OffsetDateTime.now());
        pick.recordAnswer(855, "112-855");
        when(pickRepository.findByPickDateBetween(day, day)).thenReturn(List.of(pick));
        when(service.getBucketLabels()).thenReturn(List.of("1-13", "112-855", "856-6467"));

        final ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory(day.toString(), day.toString());

        assertEquals(200, res.getStatusCode().value());
        final var score = res.getBody().scores().stream()
                .filter(s -> s.bucketLabel().equals("112-855")).findFirst().orElseThrow();
        assertEquals(5.0, score.avgPoints());
        assertEquals(1, score.rounds());
        verify(service, never()).inferBucket(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void alwaysPickReinterpretsARecordedLabelMissingFromTheCurrentBoundaries() {
        final LocalDate day = GameDate.todayUtc().minusDays(1);
        final ReviewGamePick pick = new ReviewGamePick(1L, day, 42L, OffsetDateTime.now());
        pick.recordAnswer(855, "999-1000");
        when(pickRepository.findByPickDateBetween(day, day)).thenReturn(List.of(pick));
        when(service.getBucketLabels()).thenReturn(List.of("1-13", "112-855", "856-6467"));
        when(service.inferBucket(855)).thenReturn("112-855");

        final ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory(day.toString(), day.toString());

        final var score = res.getBody().scores().stream()
                .filter(s -> s.bucketLabel().equals("112-855")).findFirst().orElseThrow();
        assertEquals(5.0, score.avgPoints());
    }

    @Test
    void alwaysPickHistoryRefusesARangeWiderThanTenYears() {
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory("1990-01-01", "2026-12-31");

        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistoryAcceptsTheSingleDayTheUiAsksFor() {
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory("2026-10-16", "2026-10-16");

        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistoryAcceptsTheUnboundedDefault() {
        // The archive summary's all-time view calls with no params; its cache key is the
        // 'min'/'max' literal pair, so the bound must not refuse it.
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory(null, null);

        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistoryAcceptsAnExplicitRangeBelowTheBound() {
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory("2026-01-01", "2026-12-31");

        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistoryRefusesAFromOnlyRangeWiderThanTenYears() {
        // A from-only request parses to from..today; with the bound gated on both params
        // being present it would be answered 200 and cached under always-pick-hist:<from>:max —
        // one shared-cache entry per client-minted value.
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory("2000-01-01", null);

        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistoryRefusesAToOnlyRangeWiderThanTenYears() {
        ResponseEntity<ReviewGameStateController.HistoricalAlwaysPickResponse> res =
                controller.alwaysPickHistory(null, "2026-12-31");

        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void blankRangeParametersCollapseToASingleAllTimeCacheEntry() {
        // The guard classifies blank from/to as the all-time view, but the cache key used to
        // insert the RAW non-null string, so every blank spelling ("", " ", ...) minted its own
        // entry in the shared cache. Drive the real @Cacheable proxy backed by an observable
        // ConcurrentMapCache and check the entry set.
        final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("review-game");
        final CacheInterceptor interceptor = new CacheInterceptor();
        interceptor.setCacheManager(cacheManager);
        interceptor.setCacheOperationSource(new AnnotationCacheOperationSource());
        // Standalone use: the bean lifecycle normally calls this, and without it the interceptor
        // bypasses caching entirely (initialized=false) instead of failing.
        interceptor.afterSingletonsInstantiated();
        final ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.addAdvice(interceptor);
        final ReviewGameStateController cachingController = (ReviewGameStateController) proxyFactory.getProxy();

        when(service.getBucketLabels()).thenReturn(List.of("A", "B"));
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of());

        assertEquals(200, cachingController.alwaysPickHistory("  ", " ").getStatusCode().value());
        assertEquals(200, cachingController.alwaysPickHistory("", "").getStatusCode().value());

        final Map<?, ?> nativeCache = (Map<?, ?>) cacheManager.getCache("review-game").getNativeCache();
        assertEquals(Set.of("always-pick-hist:min:max"), nativeCache.keySet(),
                "blank from/to must collapse onto the one all-time key, not mint an entry per blank spelling");
    }
}
