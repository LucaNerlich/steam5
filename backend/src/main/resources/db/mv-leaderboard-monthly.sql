-- Materialized view backing the rolling 30-day ("monthly") leaderboard read path
-- (LeaderboardService#buildMonthlyLeaderboard / GET /api/leaderboard/monthly).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to the 30 days ending on
-- (now() AT TIME ZONE 'UTC')::date — computed explicitly in UTC regardless of the database
-- session's timezone, matching the app's GameDate.todayUtc() anchor exactly. The window is
-- re-derived on every refresh, so it rolls forward automatically — no stored start/end dates
-- to maintain.
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
--
-- Freshness is bounded by refresh cadence (jobs.leaderboard-refresh-monthly.enabled), which
-- also means the 30-day window itself only advances at refresh time, not continuously.
CREATE MATERIALIZED VIEW mv_leaderboard_monthly AS
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
WHERE g.game_date BETWEEN (now() AT TIME ZONE 'UTC')::date - 29 AND (now() AT TIME ZONE 'UTC')::date
-- Every u.* column is listed explicitly (see mv-leaderboard-all-time.sql's header for why:
-- avoids a catalog dependency on users_pkey that would otherwise block DROP CONSTRAINT
-- operations, e.g. from pg_restore --clean).
GROUP BY g.steam_id, u.steam_id, u.persona_name, u.avatar_full, u.blurdata_avatar_full, u.profile_url
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_monthly_steam_id
    ON mv_leaderboard_monthly (steam_id);
