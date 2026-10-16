package org.steam5.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * The shared {@code review-game} cache holds the page entries every visitor reads. Nothing
 * addressed by client-supplied input may mint entries in it (see #340).
 */
class ReviewGameStateCachingTest {

    private ReviewGameStateService service;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                mock(GuessRepository.class),
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                mock(ReviewGamePickRepository.class),
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
}
