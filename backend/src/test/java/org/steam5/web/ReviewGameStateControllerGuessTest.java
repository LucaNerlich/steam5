package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.AnonymousGuessLimiter;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers two fixes:
 * <ul>
 *   <li>Anonymous /guess must not act as a free answer oracle for a still-live
 *       daily pick — it may reveal the true answer at most once per (IP, appId).</li>
 *   <li>/guess-auth must reject a bucketGuess outside the known bucket labels so
 *       it can never persist a value the nightly aggregation query would crash on.</li>
 * </ul>
 */
public class ReviewGameStateControllerGuessTest {

    private static final Long LIVE_APP_ID = 42L;
    private static final Long HISTORICAL_APP_ID = 7L;

    private ReviewGameStateService service;
    private GuessRepository guessRepository;
    private AnonymousGuessLimiter anonymousGuessLimiter;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        guessRepository = mock(GuessRepository.class);
        anonymousGuessLimiter = mock(AnonymousGuessLimiter.class);
        controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailRepository.class),
                guessRepository,
                mock(SteamAppReviewsRepository.class),
                mock(UserRepository.class),
                mock(ReviewGamePickRepository.class),
                mock(Scheduler.class),
                mock(MeterRegistry.class),
                mock(PlatformTransactionManager.class),
                anonymousGuessLimiter);

        when(service.generateDailyPicks()).thenReturn(
                List.of(new ReviewGamePick(1L, LocalDate.now(), LIVE_APP_ID, OffsetDateTime.now())));
        when(service.getTotalReviewCountForApp(LIVE_APP_ID)).thenReturn(500);
        when(service.getTotalReviewCountForApp(HISTORICAL_APP_ID)).thenReturn(50);
        when(service.inferBucket(500)).thenReturn("101-1000");
        when(service.inferBucket(50)).thenReturn("1-100");
        when(service.getBucketLabels()).thenReturn(List.of("1-100", "101-1000", "1001+"));
    }

    private static HttpServletRequest requestFrom(String ip) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    @Test
    void submitGuess_revealsAnswerOnceForALiveAppIdFromOneIp() {
        when(anonymousGuessLimiter.tryClaim("203.0.113.7", LIVE_APP_ID)).thenReturn(true);

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"),
                        requestFrom("203.0.113.7"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals("101-1000", res.getBody().actualBucket());
        assertEquals(500, res.getBody().totalReviews());
    }

    @Test
    void submitGuess_rejectsASecondGuessForTheSameIpAndLiveAppId() {
        when(anonymousGuessLimiter.tryClaim("203.0.113.7", LIVE_APP_ID)).thenReturn(false);

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "1-100"),
                        requestFrom("203.0.113.7"));

        assertEquals(429, res.getStatusCode().value());
    }

    @Test
    void submitGuess_rejectsUnknownBucketBeforeRevealingOrRateLimiting() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "not-a-bucket"),
                        requestFrom("203.0.113.7"));

        assertEquals(400, res.getStatusCode().value());
        verify(anonymousGuessLimiter, never()).tryClaim("203.0.113.7", LIVE_APP_ID);
        verify(service, never()).getTotalReviewCountForApp(LIVE_APP_ID);
    }

    @Test
    void submitGuess_usesTheEffectiveRemoteAddressAndIgnoresSpoofedForwardingHeaders() {
        final HttpServletRequest request = requestFrom("10.0.0.12");
        when(request.getHeader("x-forwarded-for")).thenReturn("203.0.113.99");
        when(anonymousGuessLimiter.tryClaim("10.0.0.12", LIVE_APP_ID)).thenReturn(true);

        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "101-1000"), request);

        assertEquals(200, res.getStatusCode().value());
        verify(anonymousGuessLimiter).tryClaim("10.0.0.12", LIVE_APP_ID);
        verify(anonymousGuessLimiter, never()).tryClaim("203.0.113.99", LIVE_APP_ID);
    }

    @Test
    void submitGuess_historicalAppIdIsNeverLimited() {
        // A historical (no longer live) appId is not gated by the limiter at all —
        // archive pages rely on repeated anonymous lookups for resolved days.
        final ResponseEntity<ReviewGameStateController.GuessResponse> first =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(HISTORICAL_APP_ID, "1-100"),
                        requestFrom("203.0.113.7"));
        final ResponseEntity<ReviewGameStateController.GuessResponse> second =
                controller.submitGuess(new ReviewGameStateController.GuessRequest(HISTORICAL_APP_ID, "1-100"),
                        requestFrom("203.0.113.7"));

        assertEquals(200, first.getStatusCode().value());
        assertEquals(200, second.getStatusCode().value());
        assertEquals(0, org.mockito.Mockito.mockingDetails(anonymousGuessLimiter).getInvocations().size(),
                "the limiter must never be consulted for a non-live appId");
    }

    @Test
    void submitGuessAuthenticated_rejectsABucketGuessOutsideTheKnownLabels() {
        final ResponseEntity<ReviewGameStateController.GuessResponse> res =
                controller.submitGuessAuthenticated("u1",
                        new ReviewGameStateController.GuessRequest(LIVE_APP_ID, "not-a-bucket"));

        assertEquals(400, res.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(guessRepository);
    }
}
