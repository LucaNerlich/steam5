package org.steam5.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private SimpleMeterRegistry meterRegistry;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        guessRepository = mock(GuessRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                guessRepository,
                mock(SteamAppReviewsRepository.class),
                mock(ReviewGamePickRepository.class),
                mock(Scheduler.class),
                meterRegistry);

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

    @Test
    void submitGuessAuthenticated_returnsThePersistedRowWhenAConcurrentInsertWins() {
        // Issue #203: the exists-check misses, the insert loses the race against
        // ix_guess_steam_date_round, and the handler must answer 200 from the
        // winner's committed row. With an ambient @Transactional this path was a
        // 500: the failed insert marked the transaction rollback-only and the
        // commit at method exit threw UnexpectedRollbackException.
        final Guess persisted = new Guess(9L, "u1", LocalDate.now(), 1, LIVE_APP_ID,
                "101-1000", "101-1000", 4, OffsetDateTime.now());
        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("u1", LocalDate.now(), 1))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(guessRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuessAuthenticated("u1",
                        new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(500, res.getBody().totalReviews());
        assertEquals("101-1000", res.getBody().actualBucket());
        assertTrue(res.getBody().correct());
        // The loser persisted nothing, so the throughput counter must not move.
        assertTrue(meterRegistry.find("steam5.guesses").counters().isEmpty());
    }

    @Test
    void submitGuessAuthenticated_gradesFromTheAnswerRecordedOnThePick() {
        // The mutable steam_app_reviews snapshot moved to a different bucket after the
        // pick was generated; the recorded answer must decide the points, otherwise the
        // stored guess disagrees with /my/today and the archive for the same round.
        final ReviewGamePick recorded = new ReviewGamePick(1L, LocalDate.now(), LIVE_APP_ID, OffsetDateTime.now());
        recorded.recordAnswer(1000, "101-1000");
        when(service.generateDailyPicks()).thenReturn(List.of(recorded));
        when(service.getTotalReviewCountForApp(LIVE_APP_ID)).thenReturn(500);
        when(service.inferBucket(500)).thenReturn("1-100");
        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("u1", LocalDate.now(), 1))
                .thenReturn(Optional.empty());

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuessAuthenticated("u1",
                        new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(1000, res.getBody().totalReviews());
        assertEquals("101-1000", res.getBody().actualBucket());
        assertTrue(res.getBody().correct());

        final org.mockito.ArgumentCaptor<Guess> saved = org.mockito.ArgumentCaptor.forClass(Guess.class);
        org.mockito.Mockito.verify(guessRepository).save(saved.capture());
        assertEquals("101-1000", saved.getValue().getActualBucket());
    }

    @Test
    void submitGuessAuthenticated_isNotWrappedInATransaction() throws Exception {
        // Transaction-semantics guard (issue #203): a transaction around the
        // handler lets a caught duplicate-key violation mark it rollback-only,
        // turning the idempotent 200 into an UnexpectedRollbackException (500)
        // at commit time. Spring falls back to the declaring class' annotation,
        // so pin both spellings.
        final Method method = ReviewGameStateController.class.getMethod(
                "submitGuessAuthenticated", String.class, ReviewGameStateController.GuessRequest.class);
        assertNull(method.getAnnotation(Transactional.class),
                "submitGuessAuthenticated must not run in an ambient transaction");
        assertNull(ReviewGameStateController.class.getAnnotation(Transactional.class),
                "a class-level @Transactional would wrap submitGuessAuthenticated too");
    }
}
