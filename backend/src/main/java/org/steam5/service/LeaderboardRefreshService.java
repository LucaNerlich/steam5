package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRefreshService {

    private final LeaderboardMvRepository leaderboardMvRepository;
    private final LeaderboardRefreshStateRepository refreshStateRepository;

    public void refreshAllTime() {
        refresh(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", leaderboardMvRepository::refreshAllTimeConcurrently, leaderboardMvRepository::refreshAllTimeFull);
    }

    public void refreshMonthly() {
        refresh(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", leaderboardMvRepository::refreshMonthlyConcurrently, leaderboardMvRepository::refreshMonthlyFull);
    }

    public void refreshWeekly() {
        refresh(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", leaderboardMvRepository::refreshWeeklyConcurrently, leaderboardMvRepository::refreshWeeklyFull);
    }

    public void refreshSeason() {
        refresh(LeaderboardType.SEASON, "mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    /**
     * REFRESH MATERIALIZED VIEW CONCURRENTLY requires the view to already be populated
     * (see mv-leaderboard-*.sql, created WITH NO DATA). Before that first population, fall
     * back to a plain REFRESH so the job self-heals instead of failing forever. On success,
     * records the refresh timestamp so LeaderboardController can report data freshness.
     */
    private void refresh(final LeaderboardType type, final String viewName, final Runnable concurrentRefresh, final Runnable fullRefresh) {
        final boolean populated = Boolean.TRUE.equals(leaderboardMvRepository.isPopulated(viewName));
        if (populated) {
            concurrentRefresh.run();
        } else {
            log.info("{} not yet populated — running non-concurrent initial REFRESH", viewName);
            fullRefresh.run();
        }
        refreshStateRepository.save(new LeaderboardRefreshState(type, OffsetDateTime.now()));
    }
}
