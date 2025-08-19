package org.steam5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.DailyPickLockRepository;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.service.SteamAppReviewsFetcher;

import java.time.LocalDate;
import java.util.List;

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
    private CacheManager cacheManager;
    private ApplicationEventPublisher eventPublisher;

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
        cacheManager = mock(CacheManager.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new ReviewGameStateService(
                reviewsRepository,
                reviewsFetcher,
                detailsFetcher,
                pickRepository,
                pickLockRepository,
                excludedAppRepository,
                config,
                cacheManager,
                eventPublisher
        );

        // Defaults for config
        config.setBucketBoundaries(List.of(100, 1000, 10000, 100000)); // yields 5 buckets
        config.setDoNotRepeatDays(3650);
        config.setMinReviewsFreshDays(0);

        // Lock acquired
        when(pickLockRepository.tryAcquire(any())).thenReturn(1);

        // No existing picks
        when(pickRepository.findByPickDate(any(LocalDate.class))).thenReturn(List.of());

        // Percentiles
        SteamAppReviewsRepository.ReviewThresholds thresholds = mock(SteamAppReviewsRepository.ReviewThresholds.class);
        when(thresholds.getLowThreshold()).thenReturn(100);
        when(thresholds.getHighThreshold()).thenReturn(10000);
        when(reviewsRepository.findPercentileThresholds(anyDouble(), anyDouble())).thenReturn(thresholds);

        // Candidates per bucket (simple distinct ids)
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

        // ANY fallback
        when(reviewsRepository.findRandomAnyAppIds(any(LocalDate.class), any(PageRequest.class)))
                .thenReturn(List.of(1000L, 1001L, 1002L));

        // Details validation succeeds; do not write anything in test
        doReturn(true).when(detailsFetcher).fetchForAppId(anyLong());

        // Save picks returns the same list with ids
        when(pickRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generatesFivePicksWithoutDbWritesInDryRunStyle() throws Exception {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertNotNull(picks);
        assertEquals(5, picks.size());
        // Ensure unique appIds
        assertEquals(picks.stream().map(ReviewGamePick::getAppId).distinct().count(), picks.size());

        // Verify details were called for each pick, but we don't assert on db writes beyond saveAll
        for (ReviewGamePick p : picks) {
            verify(detailsFetcher, atLeastOnce()).fetchForAppId(p.getAppId());
        }
        // Ensure event published for each
        verify(eventPublisher, atLeast(5)).publishEvent(any(BlurhashEncodeRequested.class));
    }

    private ReviewGameStateService stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY strategy) {
        return new ReviewGameStateService(
                reviewsRepository,
                reviewsFetcher,
                detailsFetcher,
                pickRepository,
                pickLockRepository,
                excludedAppRepository,
                config,
                cacheManager,
                eventPublisher
        ) {
            @Override
            public BUCKET_STRATEGY chooseStrategyForDate(LocalDate date) {
                return strategy;
            }
        };
    }

    @Test
    void strategyRandomProducesMixedBuckets() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.RANDOM);
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        assertEquals(5, picks.size());
    }

    @Test
    void strategyEqualCoversAllBuckets() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.EQUAL);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.EQUAL, 5, 5, LocalDate.now());
        assertEquals(5, plan.size());
        assertEquals(5, plan.stream().distinct().count());
    }

    @Test
    void strategyLeanHighSkewsHigh() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.LEAN_HIGH);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_HIGH, 5, 1000, LocalDate.now());
        final long highish = plan.stream().filter(i -> i >= 3).count();
        assertTrue(highish > 500, "expected >50% from top 2 buckets");
    }

    @Test
    void strategyLeanLowSkewsLow() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.LEAN_LOW);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_LOW, 5, 1000, LocalDate.now());
        final long lowish = plan.stream().filter(i -> i <= 1).count();
        assertTrue(lowish > 500, "expected >50% from bottom 2 buckets");
    }

    @Test
    void strategyLeanCenterSkewsCenter() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.LEAN_CENTER);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LEAN_CENTER, 5, 1000, LocalDate.now());
        final long center = plan.stream().filter(i -> i == 2).count();
        assertTrue(center > 350, "expected noticeable mass at center bucket");
    }

    @Test
    void strategyHighFocusesTopTwo() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.HIGH);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.HIGH, 5, 5, LocalDate.now());
        // first two planned slots must be from top two buckets (3 or 4)
        assertTrue(plan.get(0) >= 3 && plan.get(0) <= 4);
        assertTrue(plan.get(1) >= 3 && plan.get(1) <= 4);
    }

    @Test
    void strategyLowFocusesBottomTwo() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.LOW);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.LOW, 5, 5, LocalDate.now());
        // first two planned slots must be from bottom two buckets (0 or 1)
        assertTrue(plan.get(0) >= 0 && plan.get(0) <= 1);
        assertTrue(plan.get(1) >= 0 && plan.get(1) <= 1);
    }

    @Test
    void strategyCenterFocusesMiddle() throws Exception {
        service = stubbedServiceFor(ReviewGameStateService.BUCKET_STRATEGY.CENTER);
        final var plan = service.planBucketSelection(ReviewGameStateService.BUCKET_STRATEGY.CENTER, 5, 5, LocalDate.now());
        // first two planned slots should be the exact center index (2)
        assertEquals(2, plan.get(0));
        assertEquals(2, plan.get(1));
    }
}


