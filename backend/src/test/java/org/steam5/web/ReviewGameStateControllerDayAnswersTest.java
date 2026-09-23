package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the archive answers endpoint: it must serve a finished day's answers,
 * and must never reveal a live (today) or future day's answers — doing so would
 * be an unrestricted answer oracle that bypasses the anonymous guess limiter.
 */
public class ReviewGameStateControllerDayAnswersTest {

    private ReviewGameStateService service;
    private ReviewGamePickRepository pickRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailRepository.class),
                mock(GuessRepository.class),
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                pickRepository,
                mock(Scheduler.class),
                mock(MeterRegistry.class),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void getDayAnswers_returnsAnswersForAPastDay() {
        final LocalDate past = GameDate.todayUtc().minusDays(1);
        when(pickRepository.findByPickDate(past)).thenReturn(List.of(
                new ReviewGamePick(1L, past, 42L, OffsetDateTime.now()),
                new ReviewGamePick(2L, past, 43L, OffsetDateTime.now())));
        when(service.getTotalReviewCountForApp(42L)).thenReturn(500);
        when(service.getTotalReviewCountForApp(43L)).thenReturn(50);
        when(service.inferBucket(500)).thenReturn("101-1000");
        when(service.inferBucket(50)).thenReturn("1-100");

        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers(past.toString());

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals(2, res.getBody().size());
        assertEquals(42L, res.getBody().getFirst().appId());
        assertEquals(500, res.getBody().getFirst().totalReviews());
        assertEquals("101-1000", res.getBody().getFirst().actualBucket());
        assertEquals("public, max-age=31536000, immutable", res.getHeaders().getCacheControl());
    }

    @Test
    void getDayAnswers_rejectsTodayWithoutRevealingAnswers() {
        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers(GameDate.todayUtc().toString());

        assertEquals(404, res.getStatusCode().value());
        verify(pickRepository, never()).findByPickDate(GameDate.todayUtc());
        verify(service, never()).getTotalReviewCountForApp(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getDayAnswers_rejectsFutureDays() {
        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers(GameDate.todayUtc().plusDays(1).toString());

        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void getDayAnswers_returns404ForADayWithoutPicks() {
        final LocalDate past = GameDate.todayUtc().minusDays(5);
        when(pickRepository.findByPickDate(past)).thenReturn(List.of());

        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers(past.toString());

        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void getDayAnswers_rejectsMalformedDate() {
        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers("not-a-date");

        assertEquals(400, res.getStatusCode().value());
    }
}
