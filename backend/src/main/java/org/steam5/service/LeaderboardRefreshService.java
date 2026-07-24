package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRefreshService {

    // Arbitrary namespaced base so these keys don't collide with advisory locks taken
    // elsewhere in the app (there are none today, but this keeps that assumption explicit).
    private static final long ADVISORY_LOCK_BASE = 5_927_100L;

    private final LeaderboardMvRepository leaderboardMvRepository;
    private final LeaderboardRefreshStateRepository refreshStateRepository;

    @Transactional
    public void refreshAllTime() {
        refresh(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", leaderboardMvRepository::refreshAllTimeConcurrently, leaderboardMvRepository::refreshAllTimeFull);
    }

    @Transactional
    public void refreshMonthly() {
        refresh(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", leaderboardMvRepository::refreshMonthlyConcurrently, leaderboardMvRepository::refreshMonthlyFull);
    }

    @Transactional
    public void refreshWeekly() {
        refresh(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", leaderboardMvRepository::refreshWeeklyConcurrently, leaderboardMvRepository::refreshWeeklyFull);
    }

    @Transactional
    public void refreshSeason() {
        refresh(LeaderboardType.SEASON, "mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    @Transactional
    public void refreshHardestGames() {
        refresh(LeaderboardType.HARDEST_GAMES, "mv_hardest_games", leaderboardMvRepository::refreshHardestGamesConcurrently, leaderboardMvRepository::refreshHardestGamesFull);
    }

    /**
     * Guards against concurrent REFRESH attempts on the same view from any source — not just
     * within this JVM (Quartz's {@code @DisallowConcurrentExecution} only covers that), but
     * also across process restarts: an old process's REFRESH transaction that hasn't fully
     * released its locks yet (e.g. killed mid-flight during a redeploy or dev hot-restart) can
     * otherwise collide with a newly-started process's immediately-firing intraday trigger and
     * deadlock in Postgres. A transaction-scoped advisory lock (this method is
     * {@code @Transactional}, so the lock and the REFRESH below share one transaction/connection
     * and the lock is auto-released at commit/rollback) makes a colliding attempt skip cleanly
     * instead of blocking or deadlocking — the next scheduled tick retries.
     *
     * <p>REFRESH MATERIALIZED VIEW CONCURRENTLY also requires the view to already be populated
     * (see mv-leaderboard-*.sql, created WITH NO DATA). Before that first population, fall back
     * to a plain REFRESH so the job self-heals instead of failing forever. On success, records
     * the refresh timestamp so LeaderboardController can report data freshness.</p>
     */
    private void refresh(final LeaderboardType type, final String viewName, final Runnable concurrentRefresh, final Runnable fullRefresh) {
        final long lockKey = ADVISORY_LOCK_BASE + type.ordinal();
        if (!Boolean.TRUE.equals(leaderboardMvRepository.tryAdvisoryXactLock(lockKey))) {
            log.info("Another session is already refreshing {} — skipping this run", viewName);
            return;
        }

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
