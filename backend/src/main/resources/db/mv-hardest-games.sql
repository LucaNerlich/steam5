-- Materialized view backing the hardest-games ranking read path
-- (StatisticsService#getHardestGames / GET /api/stats/game/hardest).
--
-- Reproduces GuessRepository#findHardestGames(limit, minPlayers) exactly, with minPlayers
-- fixed at 5 (the only value ever passed in production — see StatisticsService#getHardestGames)
-- and no LIMIT (the view materializes every qualifying game, the app applies LIMIT in Java,
-- same as it always has). Ranks games by difficulty (lowest average points first) with
-- deception metrics (too-high/too-low guess counts using the same leading-numeric-bucket
-- regex comparison as the leaderboard MVs) and the single most common wrong bucket per game
-- (via a DISTINCT ON CTE).
--
-- LEFT JOINs `steam_app_index` for the app's display name (falls back to the raw app_id if
-- missing) and joins a wrong-bucket CTE derived from `guesses` — every non-grouped column
-- from those is already wrapped in an aggregate (MAX(...)/COALESCE(MAX(...))), so this view
-- does NOT rely on Postgres's functional-dependency-on-primary-key GROUP BY optimization the
-- way the leaderboard MVs originally did — no extra catalog dependency on any primary key
-- constraint. It still has an unavoidable table-level dependency on `guesses`/`steam_app_index`
-- themselves (see leaderboard-mv-maintenance.sql — this view must be dropped the same way
-- before a pg_restore --clean).
--
-- NOT managed by Hibernate ddl-auto — created/populated automatically by
-- LeaderboardMvBootstrapConfig at startup (or apply manually, see README "Query Performance
-- Notes"). Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY,
-- which requires the unique index below and prior population.
--
-- Freshness is bounded by refresh cadence (jobs.leaderboard-refresh-hardest-games.enabled /
-- QuartzConfig) — once daily only, no intraday trigger (hardest-games rankings change slowly).
CREATE MATERIALIZED VIEW mv_hardest_games AS
WITH wrong_counts AS (
    SELECT app_id, selected_bucket, COUNT(*) AS cnt
    FROM guesses
    WHERE selected_bucket <> actual_bucket
    GROUP BY app_id, selected_bucket
),
top_wrong AS (
    SELECT DISTINCT ON (app_id) app_id, selected_bucket, cnt
    FROM wrong_counts
    ORDER BY app_id, cnt DESC
)
SELECT
    g.app_id                                                            AS app_id,
    COALESCE(MAX(sai.name), CAST(g.app_id AS TEXT))                     AS app_name,
    AVG(g.points)                                                       AS avg_score,
    COUNT(DISTINCT g.steam_id)                                          AS player_count,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high_count,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low_count,
    COUNT(*)                                                            AS total_guesses,
    MAX(tw.selected_bucket)                                             AS most_common_wrong_bucket,
    MAX(tw.cnt)                                                         AS most_common_wrong_bucket_count,
    MAX(g.actual_bucket)                                                AS actual_bucket,
    MAX(g.game_date)                                                    AS latest_pick_date
FROM guesses g
LEFT JOIN steam_app_index sai ON sai.app_id = g.app_id
LEFT JOIN top_wrong tw ON tw.app_id = g.app_id
GROUP BY g.app_id
HAVING COUNT(DISTINCT g.steam_id) >= 5
ORDER BY AVG(g.points) ASC, COUNT(DISTINCT g.steam_id) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_hardest_games_app_id
    ON mv_hardest_games (app_id);
