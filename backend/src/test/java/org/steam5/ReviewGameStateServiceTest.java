package org.steam5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.GameDate;
import org.steam5.domain.ReviewGamePick;
import org.steam5.game.GameId;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.review.ReviewBucketStrategy;
import org.steam5.game.review.ReviewGameModule;
import org.steam5.game.review.ReviewPickGenerator;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.DailyPickLockRepository;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.service.SteamAppReviewsFetcher;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReviewGameStateServiceTest {

    private SteamAppReviewsRepository reviewsRepository;
    private SteamAppReviewsFetcher reviewsFetcher;
    private SteamAppDetailsFetcher detailsFetcher;
    private ReviewGamePickRepository pickRepository;
    private DailyPickLockRepository pickLockRepository;
    private ExcludedAppRepository excludedAppRepository;
    private ReviewGameConfig config;
    private DomainCacheEvictor cacheEvictor;
    private ApplicationEventPublisher eventPublisher;
    private ReviewPickGenerator pickGenerator;

    private ReviewGameStateService service;

    @BeforeEach
    void setUp() throws Exception {
        reviewsRepository = mock(SteamAppReviewsRepository.class);
        reviewsFetcher = mock(SteamAppReviewsFetcher.class);
        detailsFetcher = mock(SteamAppDetailsFetcher.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        pickLockRepository = mock(DailyPickLockRepository.class);
        excludedAppRepository = mock(ExcludedAppRepository.class);
        config = new ReviewGameConfig();
        cacheEvictor = mock(DomainCacheEvictor.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        pickGenerator = spy(new ReviewPickGenerator(
                reviewsRepository,
                reviewsFetcher,
                detailsFetcher,
                excludedAppRepository,
                config,
                eventPublisher
        ));
        final ReviewGameModule reviewGameModule = new ReviewGameModule(
                pickRepository,
                pickLockRepository,
                pickGenerator,
                cacheEvictor
        );
        service = new ReviewGameStateService(
                new DailyGameStateService(),
                reviewGameModule,
                pickGenerator,
                config,
                reviewsRepository
        );

        config.setBucketBoundaries(List.of(100, 1000, 10000, 100000));
        config.setDoNotRepeatDays(3650);
        config.setMinReviewsFreshDays(0);

        when(pickLockRepository.tryAcquire(any())).thenReturn(1);
        when(pickRepository.findByPickDate(any(LocalDate.class))).thenReturn(List.of());

        SteamAppReviewsRepository.ReviewThresholds thresholds = mock(SteamAppReviewsRepository.ReviewThresholds.class);
        when(thresholds.getLowThreshold()).thenReturn(100);
        when(thresholds.getHighThreshold()).thenReturn(10000);
        when(reviewsRepository.findPercentileThresholds(anyDouble(), anyDouble())).thenReturn(thresholds);

        when(reviewsRepository.findRandomBetween(any(LocalDate.class), eq(1), eq(100), any(PageRequest.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(reviewsRepository.findRandomBetween(any(LocalDate.class), eq(101), eq(1000), any(PageRequest.class)))
                .thenReturn(List.of(4L, 5L, 6L));
        when(reviewsRepository.findRandomBetween(any(LocalDate.class), eq(1001), eq(10000), any(PageRequest.class)))
                .thenReturn(List.of(7L, 8L, 9L));
        when(reviewsRepository.findRandomBetween(any(LocalDate.class), eq(10001), eq(100000), any(PageRequest.class)))
                .thenReturn(List.of(10L, 11L, 12L));
        when(reviewsRepository.findRandomGte(any(LocalDate.class), eq(100001), any(PageRequest.class)))
                .thenReturn(List.of(13L, 14L, 15L));

        when(reviewsRepository.findRandomAnyAppIds(any(LocalDate.class), any(PageRequest.class)))
                .thenReturn(List.of(1000L, 1001L, 1002L));

        doReturn(true).when(detailsFetcher).fetchForAppId(anyLong());
        when(pickRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubStrategy(final ReviewGameStateService.BUCKET_STRATEGY strategy) {
        doReturn(ReviewBucketStrategy.valueOf(strategy.name())).when(pickGenerator).chooseStrategyForDate(any());
    }

    @Test
    void generateDailyPicks_evictsReviewGameCacheWhenPicksCreated() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertFalse(picks.isEmpty());
        verify(cacheEvictor).evictGameState(GameId.REVIEW_GUESSER);
    }

    @Test
    void generateDailyPicks_anchorsToUtcDateNotJvmLocalDate() {
        service.generateDailyPicks();
        verify(pickRepository).findByPickDate(GameDate.todayUtc());
    }

    @Test
    void generatesFivePicksWithoutDbWritesInDryRunStyle() throws Exception {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertNotNull(picks);
        assertEquals(5, picks.size());
        assertEquals(picks.stream().map(ReviewGamePick::getAppId).distinct().count(), picks.size());

        for (ReviewGamePick p : picks) {
            verify(detailsFetcher, atLeastOnce()).fetchForAppId(p.getAppId());
        }
        verify(eventPublisher, atLeast(5)).publishEvent(any(BlurhashEncodeRequested.class));
    }

    @Test
    void strategyRandomProducesMixedBuckets() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.RANDOM);
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyEqualCoversAllBuckets() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.EQUAL);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.EQUAL, 5, 5, LocalDate.now());
        assertEquals(5, plan.size());
        assertEquals(5, plan.stream().distinct().count());
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyLeanHighSkewsHigh() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.LEAN_HIGH);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_HIGH, 5, 1000, LocalDate.now());
        final long highish = plan.stream().filter(i -> i >= 3).count();
        assertTrue(highish > 500, "expected >50% from top 2 buckets");
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyLeanLowSkewsLow() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.LEAN_LOW);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_LOW, 5, 1000, LocalDate.now());
        final long lowish = plan.stream().filter(i -> i <= 1).count();
        assertTrue(lowish > 500, "expected >50% from bottom 2 buckets");
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyLeanCenterSkewsCenter() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.LEAN_CENTER);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_CENTER, 5, 1000, LocalDate.now());
        final long center = plan.stream().filter(i -> i == 2).count();
        assertTrue(center > 350, "expected noticeable mass at center bucket");
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyHighFocusesTopTwo() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.HIGH);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.HIGH, 5, 5, LocalDate.now());
        assertTrue(plan.get(0) >= 3 && plan.get(0) <= 4);
        assertTrue(plan.get(1) >= 3 && plan.get(1) <= 4);
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyLowFocusesBottomTwo() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.LOW);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LOW, 5, 5, LocalDate.now());
        assertTrue(plan.get(0) >= 0 && plan.get(0) <= 1);
        assertTrue(plan.get(1) >= 0 && plan.get(1) <= 1);
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyCenterFocusesMiddle() throws Exception {
        stubStrategy(ReviewGameStateService.BUCKET_STRATEGY.CENTER);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.CENTER, 5, 5, LocalDate.now());
        assertEquals(2, plan.get(0));
        assertEquals(2, plan.get(1));
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyVariesAcrossDaysAndUsuallyDiffersDayToDay() {
        final LocalDate start = LocalDate.of(2024, 1, 1);
        final int window = 256;
        final Set<ReviewGameStateService.BUCKET_STRATEGY> seen = new HashSet<>();
        int changes = 0;
        ReviewGameStateService.BUCKET_STRATEGY prev = null;
        for (int i = 0; i < window; i++) {
            final LocalDate d = start.plusDays(i);
            final var s = service.chooseStrategyForDate(d);
            seen.add(s);
            if (prev != null && s != prev) {
                changes++;
            }
            prev = s;
        }
        assertTrue(seen.size() >= 6, "expected at least 6 distinct strategies across the window");
        final double changeRate = changes / (double) (window - 1);
        assertTrue(changeRate >= 0.7, "expected >=70% of consecutive days to differ, got " + changeRate);
    }
}
