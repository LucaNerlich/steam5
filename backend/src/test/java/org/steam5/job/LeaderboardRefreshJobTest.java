package org.steam5.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeaderboardRefreshJobTest {

    private LeaderboardRefreshService refreshService;
    private DomainCacheEvictor cacheEvictor;
    private LeaderboardRefreshJob job;

    @BeforeEach
    void setUp() {
        refreshService = mock(LeaderboardRefreshService.class);
        cacheEvictor = mock(DomainCacheEvictor.class);
        job = new LeaderboardRefreshJob(refreshService, cacheEvictor);
    }

    private JobExecutionContext contextFor(String type) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDataMap map = new JobDataMap();
        map.put("type", type);
        when(context.getMergedJobDataMap()).thenReturn(map);
        return context;
    }

    private JobExecutionContext contextWithoutType() {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        return context;
    }

    @Test
    void execute_allTime_refreshesThenEvictsInOrder() throws JobExecutionException {
        job.execute(contextFor("ALL_TIME"));

        InOrder order = inOrder(refreshService, cacheEvictor);
        order.verify(refreshService).refreshAllTime();
        order.verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_monthly_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("MONTHLY"));
        verify(refreshService).refreshMonthly();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_weekly_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("WEEKLY"));
        verify(refreshService).refreshWeekly();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_season_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("SEASON"));
        verify(refreshService).refreshSeason();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_refreshFails_stillEvictsThenWrapsException() {
        doThrow(new RuntimeException("boom")).when(refreshService).refreshSeason();
        JobExecutionContext context = contextFor("SEASON");

        assertThrows(JobExecutionException.class, () -> job.execute(context));

        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_unrecognizedType_throwsWithoutRefreshingOrEvicting() {
        JobExecutionContext context = contextFor("NOT_A_REAL_TYPE");

        // LeaderboardType.valueOf(...) runs before the try/finally, so an unrecognized type
        // fails fast with an unwrapped IllegalArgumentException — the finally block (and thus
        // the cache eviction) never runs. This documents current behavior; every wired
        // JobDetail bean always supplies a valid literal type, so this path isn't reachable
        // in production, only via a hypothetical misconfigured JobDataMap.
        assertThrows(IllegalArgumentException.class, () -> job.execute(context));

        verifyNoInteractions(refreshService, cacheEvictor);
    }

    @Test
    void execute_missingType_throwsWithoutRefreshingOrEvicting() {
        JobExecutionContext context = contextWithoutType();

        assertThrows(IllegalArgumentException.class, () -> job.execute(context));

        verifyNoInteractions(refreshService, cacheEvictor);
    }
}
