package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
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
    private GuessRepository guessRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        guessRepository = mock(GuessRepository.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                guessRepository,
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                pickRepository,
                mock(Scheduler.class),
                mock(MeterRegistry.class));
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
    void getDayAnswers_usesStoredAnswerNotCurrentReviews() {
        final LocalDate past = GameDate.todayUtc().minusDays(1);
        final ReviewGamePick pick = new ReviewGamePick(1L, past, 42L, OffsetDateTime.now());
        pick.recordAnswer(855, "112-855");
        when(pickRepository.findByPickDate(past)).thenReturn(List.of(pick));
        // The live snapshot has moved past the boundary since the round was played.
        when(service.getTotalReviewCountForApp(42L)).thenReturn(856);

        final ResponseEntity<List<ReviewGameStateController.DayAnswer>> res =
                controller.getDayAnswers(past.toString());

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals(855, res.getBody().getFirst().totalReviews());
        assertEquals("112-855", res.getBody().getFirst().actualBucket());
    }

    @Test
    void myDay_usesTheRecordedTotalNotTheCurrentSnapshot() {
        final LocalDate past = GameDate.todayUtc().minusDays(1);
        final ReviewGamePick pick = new ReviewGamePick(1L, past, 42L, OffsetDateTime.now());
        pick.recordAnswer(855, "112-855");
        when(pickRepository.findByPickDate(past)).thenReturn(List.of(pick));
        when(guessRepository.findAllForDay("u1", past)).thenReturn(List.of(
                new org.steam5.domain.Guess(1L, "u1", past, 1, 42L, "112-855", "112-855", 5, OffsetDateTime.now())));
        // The live snapshot has moved past the boundary since the round was played.
        when(service.getTotalReviewCountForApp(42L)).thenReturn(856);

        final ResponseEntity<?> res = controller.myDay(past.toString(), "u1");

        assertEquals(200, res.getStatusCode().value());
        final List<?> body = (List<?>) res.getBody();
        assertNotNull(body);
        final ReviewGameStateController.MyGuessDto guess = (ReviewGameStateController.MyGuessDto) body.getFirst();
        assertEquals(855, guess.totalReviews());
        assertEquals("112-855", guess.actualBucket());
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
