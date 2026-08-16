-- Materialized view backing the current-season leaderboard read path
-- (LeaderboardService#buildSeasonLeaderboard / GET /api/leaderboard/season).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to whichever `seasons` row's
-- [start_date, end_date] currently contains (now() AT TIME ZONE 'UTC')::date — computed
-- explicitly in UTC rather than using bare CURRENT_DATE, matching the app's GameDate.todayUtc()
-- anchor exactly (mirrors SeasonService#findSeasonContaining). If no season row covers that
-- date (e.g. before the first season is created), the view is empty rather than erroring.
--
-- The refresh job must run AFTER same-day season rollover/finalization (SeasonFinalizerJob,
-- 00:25 UTC) so the season boundary is settled before this view re-derives its window —
-- see QuartzConfig's 00:46 UTC trigger for this type, which intentionally has no additional
-- intraday trigger (unlike all-time/monthly/weekly).
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
CREATE MATERIALIZED VIEW mv_leaderboard_season AS
WITH current_season AS (
    SELECT start_date, end_date
    FROM seasons
    WHERE start_date <= (now() AT TIME ZONE 'UTC')::date AND end_date >= (now() AT TIME ZONE 'UTC')::date
    ORDER BY season_number DESC
    LIMIT 1
)
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
JOIN current_season cs ON g.game_date BETWEEN cs.start_date AND cs.end_date
LEFT JOIN users u ON u.steam_id = g.steam_id
-- Every u.* column is listed explicitly (see mv-leaderboard-all-time.sql's header for why:
-- avoids a catalog dependency on users_pkey that would otherwise block DROP CONSTRAINT
-- operations, e.g. from pg_restore --clean).
GROUP BY g.steam_id, u.steam_id, u.persona_name, u.avatar_full, u.blurdata_avatar_full, u.profile_url
ORDER BY SUM(g.points) DESC, g.steam_id ASC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_season_steam_id
    ON mv_leaderboard_season (steam_id);
