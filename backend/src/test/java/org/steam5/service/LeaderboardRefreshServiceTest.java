package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.repository.LeaderboardMvRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardRefreshService service;

    @BeforeEach
    void setUp() {
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new LeaderboardRefreshService(leaderboardMvRepository);
    }

    @Test
    void refreshAllTime_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeConcurrently();
        verify(leaderboardMvRepository, never()).refreshAllTimeFull();
    }

    @Test
    void refreshAllTime_whenNotPopulated_fallsBackToFullRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(false);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeFull();
        verify(leaderboardMvRepository, never()).refreshAllTimeConcurrently();
    }

    @Test
    void refreshSeason_whenIsPopulatedReturnsNull_fallsBackToFullRefresh() {
        // A null result (e.g. the view row is missing from pg_matviews) must not NPE —
        // treat it the same as "not populated" so the failure surfaces from the REFRESH
        // statement itself (missing relation) rather than a silent skip.
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_season")).thenReturn(null);

        service.refreshSeason();

        verify(leaderboardMvRepository).refreshSeasonFull();
    }

    @Test
    void refreshMonthly_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_monthly")).thenReturn(true);

        service.refreshMonthly();

        verify(leaderboardMvRepository).refreshMonthlyConcurrently();
        verify(leaderboardMvRepository, never()).refreshMonthlyFull();
    }

    @Test
    void refreshWeekly_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_weekly")).thenReturn(true);

        service.refreshWeekly();

        verify(leaderboardMvRepository).refreshWeeklyConcurrently();
        verify(leaderboardMvRepository, never()).refreshWeeklyFull();
    }
}
