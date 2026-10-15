package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the anonymous {@code /guess} endpoint (which is not rate-limited — see
 * the removal of AnonymousGuessLimiter) and the authenticated {@code /guess-auth}
 * bucket-label validation.
 */
public class ReviewGameStateControllerGuessTest {

    private static final Long LIVE_APP_ID = 42L;
    private static final Long HISTORICAL_APP_ID = 7L;

    private ReviewGameStateService service;
    private GuessRepository guessRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        guessRepository = mock(GuessRepository.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                guessRepository,
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                mock(ReviewGamePickRepository.class),
                mock(Scheduler.class),
                mock(MeterRegistry.class),
                mock(PlatformTransactionManager.class));

        when(service.generateDailyPicks()).thenReturn(
                List.of(new ReviewGamePick(1L, LocalDate.now(), LIVE_APP_ID, OffsetDateTime.now())));
        when(service.getTotalReviewCountForApp(LIVE_APP_ID)).thenReturn(500);
        when(service.getTotalReviewCountForApp(HISTORICAL_APP_ID)).thenReturn(50);
        when(service.inferBucket(500)).thenReturn("101-1000");
        when(service.inferBucket(50)).thenReturn("1-100");
        when(service.getBucketLabels()).thenReturn(List.of("1-100", "101-1000", "1001+"));
    }

    @Test
    void submitGuess_revealsTheAnswerForALiveAppId() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals("101-1000", res.getBody().actualBucket());
        assertEquals(500, res.getBody().totalReviews());
    }

    @Test
    void submitGuess_isNotLimitedAcrossRepeatedCalls() {
        // Anonymous play must not be throttled by a shared/IP-based limiter: a
        // single shared outbound IP (server-side callers) previously rejected
        // every caller after the first. Repeat submissions now all succeed.
        for (int i = 0; i < 5; i++) {
            final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                    controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"));
            assertEquals(200, res.getStatusCode().value());
        }
    }

    @Test
    void submitGuess_rejectsUnknownBucketBeforeRevealing() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "not-a-bucket"));

        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void submitGuess_historicalAppIdStillRevealsTheAnswer() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(HISTORICAL_APP_ID, "1-100"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals("1-100", res.getBody().actualBucket());
    }

    @Test
    void submitGuessAuthenticated_rejectsABucketGuessOutsideTheKnownLabels() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuessAuthenticated("u1",
                        new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "not-a-bucket"));

        assertEquals(400, res.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(guessRepository);
    }

    @Test
    void submitGuess_gradesTheOpenEndedTopLabelByEqualityNotByItsParsedRange() {
        // Labels are built as [..., "101-1000", "<lastBoundary>+"] from the configured
        // boundaries, so the open-ended label carries the same number that closes the
        // bucket below it: "1000+" parses to a lower bound of 1000, while a total of
        // exactly 1000 is in "101-1000". Grading "1000+" as correct for that total is
        // the drift this test pins down (a parsed range would accept it).
        when(service.getBucketLabels()).thenReturn(List.of("1-100", "101-1000", "1000+"));
        when(service.getTotalReviewCountForApp(LIVE_APP_ID)).thenReturn(1000);
        when(service.inferBucket(1000)).thenReturn("101-1000");

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "1000+"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals("101-1000", res.getBody().actualBucket());
        assertFalse(res.getBody().correct());
    }

    @Test
    void submitGuess_gradesAMatchingLabelAsCorrect() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"));

        assertEquals(200, res.getStatusCode().value());
        assertTrue(res.getBody().correct());
    }
}
