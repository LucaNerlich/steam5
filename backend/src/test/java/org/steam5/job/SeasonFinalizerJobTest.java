package org.steam5.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.steam5.domain.Season;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonFinalizerJobTest {

    private SeasonService seasonService;
    private LeaderboardRefreshService leaderboardRefreshService;
    private DomainCacheEvictor cacheEvictor;
    private SeasonFinalizerJob job;

    @BeforeEach
    void setUp() {
        seasonService = mock(SeasonService.class);
        leaderboardRefreshService = mock(LeaderboardRefreshService.class);
        cacheEvictor = mock(DomainCacheEvictor.class);
        job = new SeasonFinalizerJob(seasonService, leaderboardRefreshService, cacheEvictor);

        Season current = new Season();
        current.setSeasonNumber(1);
        current.setStartDate(LocalDate.now());
        current.setEndDate(LocalDate.now().plusDays(30));
        when(seasonService.ensureSeasonForDate(any(LocalDate.class))).thenReturn(current);
        when(seasonService.findActiveSeasons()).thenReturn(List.of());
    }

    @Test
    void execute_refreshesSeasonMvAfterEnsuringTodaysSeason() throws JobExecutionException {
        job.execute(mock(JobExecutionContext.class));

        // Closes the race window between the new season row existing (ensureSeasonForDate)
        // and mv_leaderboard_season reflecting it — the refresh must happen within the same
        // job execution, not wait for the separately-scheduled 00:46 UTC cron.
        InOrder order = inOrder(seasonService, leaderboardRefreshService);
        order.verify(seasonService).ensureSeasonForDate(any(LocalDate.class));
        order.verify(leaderboardRefreshService).refreshSeason();
    }

    @Test
    void execute_finalizesEndedActiveSeasonsBeforeRefreshing() throws JobExecutionException {
        Season ended = new Season();
        ended.setSeasonNumber(0);
        ended.setStartDate(LocalDate.now().minusDays(31));
        ended.setEndDate(LocalDate.now().minusDays(1));
        when(seasonService.findActiveSeasons()).thenReturn(List.of(ended));

        job.execute(mock(JobExecutionContext.class));

        // The specific season row that already ended must be finalized, and that must happen
        // before the MV refresh — otherwise the refreshed view could still reflect an award
        // computation that hasn't been finalized yet.
        InOrder order = inOrder(seasonService, leaderboardRefreshService);
        order.verify(seasonService).finalizeSeason(ended);
        order.verify(leaderboardRefreshService).refreshSeason();
    }

    @Test
    void execute_alwaysEvictsLeaderboardStaticCache() throws JobExecutionException {
        job.execute(mock(JobExecutionContext.class));

        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_refreshFails_isCaughtAndDoesNotPropagateButStillEvicts() {
        doThrow(new RuntimeException("boom")).when(leaderboardRefreshService).refreshSeason();

        // Matches this job's existing behavior for a SeasonService failure: caught and
        // logged, never rethrown as JobExecutionException. Eviction still runs in the
        // finally block regardless of the failure.
        assertDoesNotThrow(() -> job.execute(mock(JobExecutionContext.class)));

        verify(cacheEvictor).evictLeaderboardStatic();
    }
}
