package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.SteamAppReviews;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers GET /api/review-game/archive/random: a historical-day pick used to keep playing after round 5. */
public class ReviewGameStateControllerRandomArchiveTest {

    private ReviewGameStateService service;
    private SteamAppReviewsRepository reviewsRepository;
    private ReviewGamePickRepository pickRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        final SteamAppDetailService detailService = mock(SteamAppDetailService.class);
        final GuessRepository guessRepository = mock(GuessRepository.class);
        reviewsRepository = mock(SteamAppReviewsRepository.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        final Scheduler scheduler = mock(Scheduler.class);
        final MeterRegistry meterRegistry = mock(MeterRegistry.class);
        controller = new ReviewGameStateController(service, detailService, guessRepository,
                reviewsRepository, pickRepository, scheduler, meterRegistry);
    }

    @Test
    void randomArchiveDate_returnsDateWithNoStoreCacheControl() {
        final LocalDate historical = GameDate.todayUtc().minusDays(3);
        when(pickRepository.findRandomArchiveDate(GameDate.todayUtc())).thenReturn(Optional.of(historical));

        final ResponseEntity<Map<String, String>> res = controller.randomArchiveDate();

        assertEquals(200, res.getStatusCode().value());
        assertEquals(historical.toString(), res.getBody().get("date"));
        assertEquals("no-store", res.getHeaders().getCacheControl());
    }

    @Test
    void randomArchiveDate_returnsNotFoundWhenNoHistoricalDayExists() {
        when(pickRepository.findRandomArchiveDate(GameDate.todayUtc())).thenReturn(Optional.empty());

        final ResponseEntity<Map<String, String>> res = controller.randomArchiveDate();

        assertEquals(404, res.getStatusCode().value());
        assertNull(res.getBody());
    }

    @Test
    void alwaysPickHistory_malformedDatesReturn400() {
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of());
        assertEquals(400, controller.alwaysPickHistory("2024-13-99", null).getStatusCode().value());
        assertEquals(400, controller.alwaysPickHistory("2024-01-01", "garbage").getStatusCode().value());
        assertEquals(400, controller.alwaysPickHistory("2024-12-31", "2024-01-01").getStatusCode().value());
    }

    @Test
    void alwaysPickHistory_validRangeReturns200() {
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of());
        final var res = controller.alwaysPickHistory("2024-01-01", "2024-01-31");
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void alwaysPickHistory_loadsReviewCountsInOneBulkQuery() {
        // Two picks (the same app twice) plus one app with no reviews row: the range
        // computation must issue exactly one review-count query, not one per pick.
        final LocalDate day = LocalDate.parse("2024-01-01");
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of(
                new ReviewGamePick(1L, day, 42L, OffsetDateTime.now()),
                new ReviewGamePick(2L, day, 42L, OffsetDateTime.now()),
                new ReviewGamePick(3L, day, 7L, OffsetDateTime.now())));
        final SteamAppReviews reviews = mock(SteamAppReviews.class);
        when(reviews.getAppId()).thenReturn(42L);
        when(reviews.getTotalPositive()).thenReturn(4);
        when(reviews.getTotalNegative()).thenReturn(6);
        when(reviewsRepository.findAllById(any())).thenReturn(List.of(reviews));
        when(service.getBucketLabels()).thenReturn(List.of("1-100", "101-1000"));
        when(service.inferBucket(10)).thenReturn("1-100");   // 4 + 6, from the bulk result
        when(service.inferBucket(0)).thenReturn("1-100");    // app 7 has no reviews row

        final var res = controller.alwaysPickHistory("2024-01-01", "2024-01-01");

        assertEquals(200, res.getStatusCode().value());
        verify(reviewsRepository, times(1)).findAllById(any());
        verify(service, never()).getTotalReviewCountForApp(anyLong());
        // All three rounds are in "1-100", so the exact label scores full credit (5).
        assertEquals(3, res.getBody().scores().getFirst().rounds());
        assertEquals(5.0, res.getBody().scores().getFirst().avgPoints());
    }
}
