package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardRefreshStateRepository refreshStateRepository;
    private LeaderboardRefreshService service;

    @BeforeEach
    void setUp() {
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        service = new LeaderboardRefreshService(leaderboardMvRepository, refreshStateRepository);
    }

    @Test
    void refreshAllTime_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeConcurrently();
        verify(leaderboardMvRepository, never()).refreshAllTimeFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
        assertNotNull(captor.getValue().getRefreshedAt());
    }

    @Test
    void refreshAllTime_whenNotPopulated_fallsBackToFullRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(false);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeFull();
        verify(leaderboardMvRepository, never()).refreshAllTimeConcurrently();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshSeason_whenIsPopulatedReturnsNull_fallsBackToFullRefreshAndRecordsState() {
        // A null result (e.g. the view row is missing from pg_matviews) must not NPE —
        // treat it the same as "not populated" so the failure surfaces from the REFRESH
        // statement itself (missing relation) rather than a silent skip.
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_season")).thenReturn(null);

        service.refreshSeason();

        verify(leaderboardMvRepository).refreshSeasonFull();
        verify(leaderboardMvRepository, never()).refreshSeasonConcurrently();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.SEASON, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshMonthly_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_monthly")).thenReturn(true);

        service.refreshMonthly();

        verify(leaderboardMvRepository).refreshMonthlyConcurrently();
        verify(leaderboardMvRepository, never()).refreshMonthlyFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.MONTHLY, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshWeekly_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_weekly")).thenReturn(true);

        service.refreshWeekly();

        verify(leaderboardMvRepository).refreshWeeklyConcurrently();
        verify(leaderboardMvRepository, never()).refreshWeeklyFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.WEEKLY, captor.getValue().getLeaderboardType());
    }
}
