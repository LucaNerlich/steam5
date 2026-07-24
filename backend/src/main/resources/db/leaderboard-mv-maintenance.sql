-- Manual utility queries for the four leaderboard materialized views
-- (mv_leaderboard_all_time, mv_leaderboard_monthly, mv_leaderboard_weekly,
-- mv_leaderboard_season — see mv-leaderboard-*.sql for their definitions).
-- Not run automatically by anything; copy/paste the statement you need into a SQL console.

-- =============================================================================
-- A) Drop before a pg_restore --clean (or any operation that drops/recreates
--    `users` or `guesses`)
-- =============================================================================
--
-- Needed because: these MVs query `FROM guesses g LEFT JOIN users u ON ...`, so Postgres
-- tracks a dependency from each MV onto both the `guesses` and `users` tables. `pg_restore
-- --clean --if-exists` issues plain `DROP TABLE IF EXISTS public.users;` / `public.guesses;`
-- statements (with no CASCADE), which fail with:
--   ERROR: cannot drop table public.users because other objects depend on it
--   DETAIL: materialized view public.mv_leaderboard_all_time depends on table public.users
-- pg_restore has no flag to make its own generated DROP statements use CASCADE, so the MVs
-- must be dropped manually first. Run this once, immediately before the pg_restore command:
DROP MATERIALIZED VIEW IF EXISTS mv_leaderboard_all_time, mv_leaderboard_monthly, mv_leaderboard_weekly, mv_leaderboard_season, mv_hardest_games CASCADE;

-- After the restore completes, just restart the backend — LeaderboardMvBootstrapConfig
-- recreates all four MVs and their unique indexes, and immediately populates each with a
-- one-time REFRESH (recording it in leaderboard_refresh_state), with no further manual step.

-- =============================================================================
-- B) Read the current contents of each materialized view
-- =============================================================================

SELECT * FROM mv_leaderboard_all_time ORDER BY total_points DESC;

SELECT * FROM mv_leaderboard_monthly ORDER BY total_points DESC;

SELECT * FROM mv_leaderboard_weekly ORDER BY total_points DESC;

SELECT * FROM mv_leaderboard_season ORDER BY total_points DESC;

SELECT * FROM mv_hardest_games ORDER BY avg_score ASC, player_count DESC;

-- =============================================================================
-- C) Check whether each view has ever been populated / when it was last refreshed
-- =============================================================================
-- (Bonus, closely related to (B) — a materialized view created WITH NO DATA raises
-- "has not been populated" on any SELECT until its first REFRESH.)

SELECT matviewname, ispopulated FROM pg_matviews WHERE matviewname LIKE 'mv_leaderboard_%' OR matviewname = 'mv_hardest_games';

SELECT * FROM leaderboard_refresh_state;
