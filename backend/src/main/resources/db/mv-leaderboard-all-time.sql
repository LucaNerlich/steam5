-- Materialized view backing the all-time leaderboard read path
-- (LeaderboardService#buildAllTimeLeaderboard / GET /api/leaderboard/all).
--
-- Reproduces the aggregation logic that once lived in GuessRepository#aggregateAllTimeStats()
-- (now removed — replaced by this MV): same GROUP BY steam_id, same too-high/too-low
-- leading-numeric-bucket regex comparison. LEFT JOINs `users` so a guess from a steam_id
-- with no `users` row still appears (profile columns come back NULL, matching
-- LeaderboardService's existing null-safe fallback) instead of being silently dropped by
-- an INNER JOIN.
--
-- NOT managed by Hibernate ddl-auto — apply manually, same convention as
-- idx_guesses_game_date (see README "Query Performance Notes"). Refreshed by
-- LeaderboardRefreshJob (org.steam5.job) via REFRESH MATERIALIZED VIEW CONCURRENTLY, which
-- requires the unique index below AND that the view already be populated at least once —
-- LeaderboardRefreshService detects an unpopulated view (pg_matviews.ispopulated) and falls
-- back to a plain REFRESH automatically the first time it runs.
--
-- Freshness is bounded by refresh cadence (see jobs.leaderboard-refresh-all-time.enabled /
-- QuartzConfig), not by the leaderboard-static Caffeine TTL.
CREATE MATERIALIZED VIEW mv_leaderboard_all_time AS
SELECT
    g.steam_id                                                          AS steam_id,
    SUM(g.points)                                                       AS total_points,
    COUNT(*)                                                            AS rounds,
    SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
    SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                       AS flops,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low,
    AVG(g.points)                                                       AS avg_points,
    u.persona_name                                                      AS persona_name,
    u.avatar_full                                                       AS avatar_full,
    u.blurdata_avatar_full                                              AS blurdata_avatar_full,
    u.profile_url                                                       AS profile_url
FROM guesses g
LEFT JOIN users u ON u.steam_id = g.steam_id
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement (e.g. a single psql command),
-- not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_all_time_steam_id
    ON mv_leaderboard_all_time (steam_id);
