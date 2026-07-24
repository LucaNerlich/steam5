-- Materialized view backing the rolling 7-day ("floating weekly") leaderboard read path
-- (LeaderboardService#buildWeeklyLeaderboard / GET /api/leaderboard/weekly?floating=true).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to the 7 days ending on
-- (now() AT TIME ZONE 'UTC')::date. See mv-leaderboard-monthly.sql's header for why this is
-- computed explicitly in UTC rather than using bare CURRENT_DATE.
--
-- Does NOT back the non-floating `/weekly` variant (the previous full Monday-Sunday week) —
-- that window doesn't roll the same way, and LeaderboardController#weekly keeps computing it
-- live via GuessRepository#findAllBetween.
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
CREATE MATERIALIZED VIEW mv_leaderboard_weekly AS
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
WHERE g.game_date BETWEEN (now() AT TIME ZONE 'UTC')::date - 6 AND (now() AT TIME ZONE 'UTC')::date
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_weekly_steam_id
    ON mv_leaderboard_weekly (steam_id);
