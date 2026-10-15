-- One-time migration for the #198 perfect-days app-name delimiter fix.
--
-- LeaderboardMvBootstrapConfig skips CREATE MATERIALIZED VIEW when the view
-- already exists, so the updated mv-perfect-days.sql (app names joined with
-- chr(31) instead of ', ', which is a delimiter Steam titles can contain and
-- therefore split real titles into extra entries) does NOT apply to existing
-- deployments automatically. Run once, then restart the backend (bootstrap
-- recreates the view and refreshes it):

DROP MATERIALIZED VIEW mv_perfect_days;
