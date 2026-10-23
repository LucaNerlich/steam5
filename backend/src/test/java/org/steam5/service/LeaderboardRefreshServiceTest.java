package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeaderboardRefreshServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardRefreshStateRepository refreshStateRepository;
    private JdbcTemplate jdbcTemplate;
    private LeaderboardRefreshService service;

    @BeforeEach
    void setUp() {
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new LeaderboardRefreshService(leaderboardMvRepository, refreshStateRepository, jdbcTemplate);
        when(leaderboardMvRepository.tryAdvisoryXactLock(anyLong())).thenReturn(true);
    }

    @Test
    void whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);
        when(leaderboardMvRepository.hasValidUniqueIndex("mv_leaderboard_all_time")).thenReturn(true);

        service.refresh(LeaderboardType.ALL_TIME);

        verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_all_time");
        final ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
        assertNotNull(captor.getValue().getRefreshedAt());
    }

    @Test
    void whenNotPopulated_fallsBackToFullRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(false);

        service.refresh(LeaderboardType.ALL_TIME);

        verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW mv_leaderboard_all_time");
        final ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
    }

    @Test
    void whenTheUniqueIndexIsInvalid_fallsBackToFullRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);
        when(leaderboardMvRepository.hasValidUniqueIndex("mv_leaderboard_all_time")).thenReturn(false);

        service.refresh(LeaderboardType.ALL_TIME);

        verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW mv_leaderboard_all_time");
    }

    @Test
    void whenIsPopulatedReturnsNull_fallsBackToFullRefresh() {
        // A missing pg_matviews row must not NPE; the REFRESH surfaces the missing relation.
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_season")).thenReturn(null);

        service.refresh(LeaderboardType.SEASON);

        verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW mv_leaderboard_season");
    }

    @Test
    void enumViewNamesAreTheExpectedLiterals() {
        assertEquals("mv_leaderboard_all_time", LeaderboardType.ALL_TIME.viewName());
        assertEquals("mv_leaderboard_monthly", LeaderboardType.MONTHLY.viewName());
        assertEquals("mv_leaderboard_weekly", LeaderboardType.WEEKLY.viewName());
        assertEquals("mv_leaderboard_season", LeaderboardType.SEASON.viewName());
        assertEquals("mv_hardest_games", LeaderboardType.HARDEST_GAMES.viewName());
        assertEquals("mv_perfect_days", LeaderboardType.PERFECT_DAYS.viewName());
    }

    @Test
    void everyTypeUsesItsWhitelistedViewName() {
        when(leaderboardMvRepository.isPopulated(anyString())).thenReturn(true);
        when(leaderboardMvRepository.hasValidUniqueIndex(anyString())).thenReturn(true);

        for (final LeaderboardType type : LeaderboardType.values()) {
            service.refresh(type);
        }

        for (final LeaderboardType type : LeaderboardType.values()) {
            verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + type.viewName());
        }
    }

    @Test
    void whenAdvisoryLockNotAcquired_skipsEntirely() {
        when(leaderboardMvRepository.tryAdvisoryXactLock(anyLong())).thenReturn(false);

        service.refresh(LeaderboardType.MONTHLY);

        verify(leaderboardMvRepository, never()).isPopulated(anyString());
        verifyNoInteractions(jdbcTemplate, refreshStateRepository);
    }

    @Test
    void whenAdvisoryLockReturnsNull_treatedAsNotAcquiredAndSkips() {
        when(leaderboardMvRepository.tryAdvisoryXactLock(anyLong())).thenReturn(null);

        service.refresh(LeaderboardType.ALL_TIME);

        verifyNoInteractions(jdbcTemplate, refreshStateRepository);
    }
}
