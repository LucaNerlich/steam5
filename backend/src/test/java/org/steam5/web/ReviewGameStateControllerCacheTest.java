package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.AuthTokenService;
import org.steam5.service.ReviewGameStateService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the cache-safety fixes:
 * <ul>
 *   <li>Per-user endpoints (/my/today, /my/day) must return {@code private} so a
 *       shared/CDN cache can never serve one user's guesses to another.</li>
 *   <li>The conditional handlers must compute 304/200 per request, and the
 *       {@code @Cacheable(unless = ...)} expression must skip caching any non-2xx
 *       response so a bodyless 304 is never replayed to a non-conditional client.</li>
 * </ul>
 */
public class ReviewGameStateControllerCacheTest {

    private ReviewGameStateService service;
    private SteamAppDetailRepository detailRepository;
    private GuessRepository guessRepository;
    private SteamAppReviewsRepository reviewsRepository;
    private UserRepository userRepository;
    private AuthTokenService authTokenService;
    private ReviewGamePickRepository pickRepository;
    private Scheduler scheduler;
    private MeterRegistry meterRegistry;

    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewGameStateService.class);
        detailRepository = mock(SteamAppDetailRepository.class);
        guessRepository = mock(GuessRepository.class);
        reviewsRepository = mock(SteamAppReviewsRepository.class);
        userRepository = mock(UserRepository.class);
        authTokenService = mock(AuthTokenService.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        scheduler = mock(Scheduler.class);
        meterRegistry = mock(MeterRegistry.class);
        controller = new ReviewGameStateController(service, detailRepository, guessRepository,
                reviewsRepository, userRepository, authTokenService, pickRepository, scheduler, meterRegistry);
    }

    private static HttpHeaders bearer(String token) {
        final HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return h;
    }

    // --- Finding 2: per-user data must never be publicly cacheable ---

    @Test
    void myToday_returnsPrivateCacheControl() {
        when(authTokenService.verifyToken("tok")).thenReturn("u1");
        when(service.generateDailyPicks())
                .thenReturn(List.of(new ReviewGamePick(1L, LocalDate.now(), 42L, OffsetDateTime.now())));
        when(guessRepository.findAllForDay("u1", LocalDate.now())).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myToday(bearer("tok"), null);

        assertEquals(200, res.getStatusCode().value());
        final String cc = res.getHeaders().getCacheControl();
        assertEquals("private, max-age=60, must-revalidate", cc);
        assertFalse(cc.contains("public"), "per-user response must not be publicly cacheable");
    }

    @Test
    void myDay_today_returnsPrivateLiveCacheControl() {
        when(authTokenService.verifyToken("tok")).thenReturn("u1");
        final LocalDate today = LocalDate.now();
        when(guessRepository.findAllForDay("u1", today)).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myDay(today.toString(), bearer("tok"), null);

        assertEquals(200, res.getStatusCode().value());
        final String cc = res.getHeaders().getCacheControl();
        assertEquals("private, max-age=60, must-revalidate", cc);
        assertFalse(cc.contains("public"));
    }

    @Test
    void myDay_pastDate_returnsPrivateImmutableCacheControl() {
        when(authTokenService.verifyToken("tok")).thenReturn("u1");
        when(guessRepository.findAllForDay("u1", LocalDate.parse("2020-01-01"))).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myDay("2020-01-01", bearer("tok"), null);

        assertEquals(200, res.getStatusCode().value());
        final String cc = res.getHeaders().getCacheControl();
        assertEquals("private, max-age=31536000, immutable", cc);
        assertFalse(cc.contains("public"));
    }

    // --- Finding 1: per-request 304/200 negotiation ---

    @Test
    void todayDetails_negotiates304PerRequest() {
        final SteamAppDetail detail = mock(SteamAppDetail.class);
        when(detail.getAppId()).thenReturn(42L);
        when(service.generateDailyPicks())
                .thenReturn(List.of(new ReviewGamePick(1L, LocalDate.now(), 42L, OffsetDateTime.now())));
        when(detailRepository.findAllByAppIdIn(anyList())).thenReturn(List.of(detail));

        // No If-None-Match -> 200 with body + ETag + live (public) cache header.
        final ResponseEntity<List<SteamAppDetail>> ok = controller.getTodayDetails(new HttpHeaders());
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getBody());
        assertEquals(1, ok.getBody().size());
        assertEquals("public, s-maxage=1800, max-age=60, must-revalidate", ok.getHeaders().getCacheControl());
        final String etag = ok.getHeaders().getETag();
        assertNotNull(etag);

        // Matching If-None-Match -> 304 with no body (built fresh from the request).
        final HttpHeaders conditional = new HttpHeaders();
        conditional.set(HttpHeaders.IF_NONE_MATCH, etag);
        final ResponseEntity<List<SteamAppDetail>> notModified = controller.getTodayDetails(conditional);
        assertEquals(304, notModified.getStatusCode().value());
        assertNull(notModified.getBody());
    }

    // --- Finding 1: the @Cacheable(unless) expression must skip caching non-2xx ---

    @Test
    void cacheUnlessExpression_cachesOnly2xxResponses() throws Exception {
        final Field f = ReviewGameStateController.class.getDeclaredField("CACHE_ONLY_2XX");
        f.setAccessible(true);
        final String exprText = (String) f.get(null);
        final Expression expr = new SpelExpressionParser().parseExpression(exprText);

        // unless == false  => the result IS cached
        assertFalse(unless(expr, ResponseEntity.ok().body("payload")), "200 OK must be cached");
        // unless == true   => the result is NOT cached
        assertTrue(unless(expr, ResponseEntity.status(304).build()), "304 must never be cached");
        assertTrue(unless(expr, ResponseEntity.notFound().build()), "404 must not be cached");
        assertTrue(unless(expr, null), "null must not be cached");
    }

    private static boolean unless(Expression expr, ResponseEntity<?> result) {
        final StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("result", result);
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }
}
