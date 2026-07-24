package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.repository.LeaderboardMvRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRefreshService {

    private final LeaderboardMvRepository leaderboardMvRepository;

    public void refreshAllTime() {
        refresh("mv_leaderboard_all_time", leaderboardMvRepository::refreshAllTimeConcurrently, leaderboardMvRepository::refreshAllTimeFull);
    }

    public void refreshMonthly() {
        refresh("mv_leaderboard_monthly", leaderboardMvRepository::refreshMonthlyConcurrently, leaderboardMvRepository::refreshMonthlyFull);
    }

    public void refreshWeekly() {
        refresh("mv_leaderboard_weekly", leaderboardMvRepository::refreshWeeklyConcurrently, leaderboardMvRepository::refreshWeeklyFull);
    }

    public void refreshSeason() {
        refresh("mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    /**
     * REFRESH MATERIALIZED VIEW CONCURRENTLY requires the view to already be populated
     * (see mv-leaderboard-*.sql, created WITH NO DATA). Before that first population, fall
     * back to a plain REFRESH so the job self-heals instead of failing forever.
     */
    private void refresh(final String viewName, final Runnable concurrentRefresh, final Runnable fullRefresh) {
        final boolean populated = Boolean.TRUE.equals(leaderboardMvRepository.isPopulated(viewName));
        if (populated) {
            concurrentRefresh.run();
        } else {
            log.info("{} not yet populated — running non-concurrent initial REFRESH", viewName);
            fullRefresh.run();
        }
    }
}
