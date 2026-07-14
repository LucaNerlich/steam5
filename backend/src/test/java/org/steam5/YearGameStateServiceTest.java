package org.steam5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.review.ReviewBucketStrategy;
import org.steam5.game.year.*;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.service.YearGameStateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class YearGameStateServiceTest {

    private SteamAppDetailRepository detailRepository;
    private SteamAppDetailsFetcher detailsFetcher;
    private YearGamePickRepository pickRepository;
    private YearGamePickLockRepository pickLockRepository;
    private ExcludedAppRepository excludedAppRepository;
    private YearGameConfig config;
    private DomainCacheEvictor cacheEvictor;
    private ApplicationEventPublisher eventPublisher;
    private YearPickGenerator pickGenerator;

    private YearGameStateService service;

    @BeforeEach
    void setUp() throws Exception {
        detailRepository = mock(SteamAppDetailRepository.class);
        detailsFetcher = mock(SteamAppDetailsFetcher.class);
        pickRepository = mock(YearGamePickRepository.class);
        pickLockRepository = mock(YearGamePickLockRepository.class);
        excludedAppRepository = mock(ExcludedAppRepository.class);
        config = new YearGameConfig();
        cacheEvictor = mock(DomainCacheEvictor.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        pickGenerator = spy(new YearPickGenerator(
                detailRepository,
                detailsFetcher,
                excludedAppRepository,
                config,
                eventPublisher
        ));

        final YearGameModule yearGameModule = new YearGameModule(
                pickRepository,
                pickLockRepository,
                pickGenerator,
                cacheEvictor
        );

        service = new YearGameStateService(
                new DailyGameStateService(),
                yearGameModule,
                pickGenerator,
                config,
                detailRepository
        );

        config.setBucketBoundaries(List.of(1999, 2009, 2019));
        config.setDoNotRepeatDays(3650);
        config.setRoundsPerDay(3);

        when(pickLockRepository.tryAcquire(any())).thenReturn(1);
        when(pickRepository.findByPickDate(any(LocalDate.class))).thenReturn(List.of());
        when(pickRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(detailsFetcher.fetchForAppId(anyLong())).thenReturn(true);

        when(detailRepository.findByAppId(anyLong())).thenAnswer(invocation -> {
            final Long appId = invocation.getArgument(0);
            final SteamAppDetail detail = new SteamAppDetail();
            detail.setAppId(appId);
            detail.setReleaseDate("19 Nov, 2020");
            return java.util.Optional.of(detail);
        });

        when(detailRepository.findRandomByReleaseYearBetween(any(LocalDate.class), eq(1), eq(1999), any(PageRequest.class)))
                .thenReturn(List.of(1L, 2L));
        when(detailRepository.findRandomByReleaseYearBetween(any(LocalDate.class), eq(2000), eq(2009), any(PageRequest.class)))
                .thenReturn(List.of(3L, 4L));
        when(detailRepository.findRandomByReleaseYearBetween(any(LocalDate.class), eq(2010), eq(2019), any(PageRequest.class)))
                .thenReturn(List.of(5L, 6L));
        when(detailRepository.findRandomByReleaseYearGte(any(LocalDate.class), eq(2020), any(PageRequest.class)))
                .thenReturn(List.of(7L, 8L));
        when(detailRepository.findRandomAnyReleaseYear(any(LocalDate.class), any(PageRequest.class)))
                .thenReturn(List.of(9L, 10L));
    }

    @Test
    void generateDailyPicks_createsConfiguredRoundCount() {
        final var picks = service.generateDailyPicks();
        assertEquals(3, picks.size());
        final Set<Long> appIds = picks.stream().map(YearGamePick::getAppId).collect(Collectors.toSet());
        assertEquals(3, appIds.size());
        verify(pickRepository).saveAll(anyList());
        verify(eventPublisher, atLeastOnce()).publishEvent(any(BlurhashEncodeRequested.class));
    }

    @Test
    void bucketLabels_matchConfig() {
        assertEquals(List.of("1-1999", "2000-2009", "2010-2019", "2019+"), service.getBucketLabels());
    }

    @Test
    void planBucketSelection_equalStrategyCoversAllBucketsForThreeRounds() {
        final List<Integer> plan = service.planBucketSelection(
                ReviewBucketStrategy.EQUAL, 4, 3, LocalDate.of(2026, 3, 1));
        assertEquals(3, plan.size());
        assertTrue(plan.stream().allMatch(i -> i >= 0 && i < 4));
    }
}
