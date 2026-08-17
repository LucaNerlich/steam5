-- Materialized view backing the perfect-days leaderboard read path
-- (StatisticsService#getPerfectDays / GET /api/stats/game/perfect-days).
--
-- A "perfect day" is when a player scored the maximum possible points on every
-- round for a given game date. The maximum is derived from the day's actual
-- round count (MAX(round_index) over that day's guesses) — NOT hardcoded to 5,
-- because the pick count is configurable and fallback logic can produce fewer
-- rounds. This mirrors GuessRepository#findUsersByPerfectDaysDesc (day_rounds
-- CTE), which compares day_points against 5 * rounds_per_day.
--
-- The correlated subquery for app_names joins review_game_picks (indexed by
-- pick_date) to fetch the games that appeared on each date — same for every
-- player that day, but stored per-row for simplicity.
--
-- NOT managed by Hibernate ddl-auto — created/populated automatically by
-- LeaderboardMvBootstrapConfig at startup (or apply manually, see README "Query
-- Performance Notes"). Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED
-- VIEW CONCURRENTLY, which requires the unique index below and prior population.
--
-- Freshness is bounded by refresh cadence (jobs.leaderboard-refresh-perfect-days.enabled /
-- QuartzConfig) — once daily only, no intraday trigger (perfect-days rankings change slowly).
CREATE MATERIALIZED VIEW mv_perfect_days AS
SELECT
    g.steam_id                                                           AS steam_id,
    COALESCE(u.persona_name, g.steam_id)                                 AS persona_name,
    MAX(u.avatar_full)                                                   AS avatar_full,
    MAX(u.blurdata_avatar_full)                                          AS blurdata_avatar_full,
    MAX(u.profile_url)                                                   AS profile_url,
    g.game_date                                                          AS game_date,
    (SELECT string_agg(COALESCE(sai2.name, CAST(rgp.app_id AS TEXT)), ', ' ORDER BY rgp.created_at)
     FROM review_game_pick rgp
     LEFT JOIN steam_app_index sai2 ON sai2.app_id = rgp.app_id
     WHERE rgp.pick_date = g.game_date)                                  AS app_names
FROM guesses g
LEFT JOIN users u ON u.steam_id = g.steam_id
GROUP BY g.steam_id, u.persona_name, g.game_date
HAVING SUM(g.points) = 5 * (SELECT MAX(d2.round_index) FROM guesses d2 WHERE d2.game_date = g.game_date)
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_perfect_days_entry
    ON mv_perfect_days (steam_id, game_date);
