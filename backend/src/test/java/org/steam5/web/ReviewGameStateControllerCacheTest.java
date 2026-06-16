package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
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
        pickRepository = mock(ReviewGamePickRepository.class);
        scheduler = mock(Scheduler.class);
        meterRegistry = mock(MeterRegistry.class);
        controller = new ReviewGameStateController(service, detailRepository, guessRepository,
                reviewsRepository, userRepository, pickRepository, scheduler, meterRegistry);
    }

    // --- Finding 2: per-user data must never be publicly cacheable ---

    @Test
    void myToday_returnsPrivateCacheControl() {
        when(service.generateDailyPicks())
                .thenReturn(List.of(new ReviewGamePick(1L, LocalDate.now(), 42L, OffsetDateTime.now())));
        when(guessRepository.findAllForDay("u1", LocalDate.now())).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myToday("u1");

        assertEquals(200, res.getStatusCode().value());
        final String cc = res.getHeaders().getCacheControl();
        assertEquals("private, max-age=60, must-revalidate", cc);
        assertFalse(cc.contains("public"), "per-user response must not be publicly cacheable");
    }

    // --- Stale-pick safety: guesses for regenerated picks must not be surfaced ---

    @Test
    @SuppressWarnings("unchecked")
    void myToday_dropsGuessesNotBelongingToCurrentPicks() {
        final LocalDate today = LocalDate.now();
        // Today's current picks contain appId 42 only (the round was regenerated).
        when(service.generateDailyPicks())
                .thenReturn(List.of(new ReviewGamePick(1L, today, 42L, OffsetDateTime.now())));
        // The user has two guesses dated today: one for the current pick (42) and one
        // left over from a pick that has since been replaced (99).
        final Guess current = new Guess(1L, "u1", today, 1, 42L, "1-100", "1-100", 5, OffsetDateTime.now());
        final Guess stale = new Guess(2L, "u1", today, 1, 99L, "101-1000", "1-100", 0, OffsetDateTime.now());
        when(guessRepository.findAllForDay("u1", today)).thenReturn(List.of(current, stale));

        final ResponseEntity<?> res = controller.myToday("u1");

        assertEquals(200, res.getStatusCode().value());
        final List<ReviewGameStateController.MyGuessDto> body =
                (List<ReviewGameStateController.MyGuessDto>) res.getBody();
        assertNotNull(body);
        assertEquals(1, body.size(), "only the guess matching a current pick should remain");
        assertEquals(42L, body.getFirst().appId());
    }

    @Test
    void myDay_today_returnsPrivateLiveCacheControl() {
        final LocalDate today = LocalDate.now();
        when(guessRepository.findAllForDay("u1", today)).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myDay(today.toString(), "u1");

        assertEquals(200, res.getStatusCode().value());
        final String cc = res.getHeaders().getCacheControl();
        assertEquals("private, max-age=60, must-revalidate", cc);
        assertFalse(cc.contains("public"));
    }

    @Test
    void myDay_pastDate_returnsPrivateImmutableCacheControl() {
        when(guessRepository.findAllForDay("u1", LocalDate.parse("2020-01-01"))).thenReturn(List.of());

        final ResponseEntity<?> res = controller.myDay("2020-01-01", "u1");

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

    // --- Rollover gap: the live "today" endpoints must never cache an empty round, so a
    // race during post-midnight lazy generation can't pin a not-yet-regenerated day. ---

    @Test
    void getToday_doesNotCacheEmptyRound() throws Exception {
        final Expression expr = cacheUnlessExpr("CACHE_ONLY_2XX_NONEMPTY_PICKS");

        final ReviewGameStateController.ReviewGameStateDto empty =
                new ReviewGameStateController.ReviewGameStateDto(LocalDate.now(), List.of(), List.of(), List.of());
        assertTrue(unless(expr, ResponseEntity.ok(empty)), "an empty round must NOT be cached");

        final SteamAppDetail detail = mock(SteamAppDetail.class);
        final ReviewGameStateController.ReviewGameStateDto populated =
                new ReviewGameStateController.ReviewGameStateDto(LocalDate.now(), List.of("1-100"), List.of("Title"), List.of(detail));
        assertFalse(unless(expr, ResponseEntity.ok(populated)), "a populated round must be cached");

        assertTrue(unless(expr, ResponseEntity.status(304).build()), "304 must never be cached");
    }

    @Test
    void getTodayDetails_doesNotCacheEmptyList() throws Exception {
        final Expression expr = cacheUnlessExpr("CACHE_ONLY_2XX_NONEMPTY_LIST");

        assertTrue(unless(expr, ResponseEntity.ok(List.of())), "an empty details list must NOT be cached");
        assertFalse(unless(expr, ResponseEntity.ok(List.of(mock(SteamAppDetail.class)))), "a non-empty list must be cached");
        assertTrue(unless(expr, ResponseEntity.status(304).build()), "304 must never be cached");
    }

    /** Reads a private static SpEL string constant from the controller and parses it. */
    private static Expression cacheUnlessExpr(String constantName) throws Exception {
        final Field f = ReviewGameStateController.class.getDeclaredField(constantName);
        f.setAccessible(true);
        return new SpelExpressionParser().parseExpression((String) f.get(null));
    }

    private static boolean unless(Expression expr, ResponseEntity<?> result) {
        final StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("result", result);
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }

    // --- Stale-round root cause: the live cache keys must embed today's UTC date so a
    // previous day's cached entry can never be served after the 00:00 UTC rollover. ---

    @Test
    void getToday_cacheKeyEmbedsTodayUtcDate() throws Exception {
        assertEquals("today-picks:" + GameDate.todayUtc(),
                evalCacheKey("getToday", HttpHeaders.class),
                "the /today cache key must be scoped to the UTC date, not a static 'picks' key");
    }

    @Test
    void getTodayDetails_cacheKeyEmbedsTodayUtcDate() throws Exception {
        assertEquals("today-details:" + GameDate.todayUtc(),
                evalCacheKey("getTodayDetails", HttpHeaders.class),
                "the /today/details cache key must be scoped to the UTC date");
    }

    /** Reads a method's @Cacheable key SpEL and evaluates it (the key uses a T(GameDate) static call). */
    private static String evalCacheKey(String method, Class<?>... params) throws Exception {
        final Cacheable c = ReviewGameStateController.class.getMethod(method, params).getAnnotation(Cacheable.class);
        final Expression expr = new SpelExpressionParser().parseExpression(c.key());
        return expr.getValue(new StandardEvaluationContext(), String.class);
    }
}
