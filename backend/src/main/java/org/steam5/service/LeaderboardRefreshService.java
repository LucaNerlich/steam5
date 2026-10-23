package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

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
    @Transactional
    public void refresh(final LeaderboardType type) {
        final String viewName = type.viewName();
        final long lockKey = ADVISORY_LOCK_BASE + type.ordinal();
        if (!Boolean.TRUE.equals(leaderboardMvRepository.tryAdvisoryXactLock(lockKey))) {
            log.info("Another session is already refreshing {} — skipping this run", viewName);
            return;
        }

        final boolean populated = Boolean.TRUE.equals(leaderboardMvRepository.isPopulated(viewName));
        final boolean concurrent = populated && leaderboardMvRepository.hasValidUniqueIndex(viewName);
        if (!populated) {
            log.info("{} not yet populated — running non-concurrent initial REFRESH", viewName);
        } else if (!concurrent) {
            log.warn("{} has no valid unique index — running non-concurrent REFRESH", viewName);
        }
        // viewName comes from the enum's whitelist, never from a caller-supplied string.
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW " + (concurrent ? "CONCURRENTLY " : "") + viewName);
        refreshStateRepository.save(new LeaderboardRefreshState(type, OffsetDateTime.now()));
    }
}
