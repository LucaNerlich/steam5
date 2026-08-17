-- One-time migration for the #171 perfect-days definition fix.
--
-- LeaderboardMvBootstrapConfig skips CREATE MATERIALIZED VIEW when the view
-- already exists, so the updated mv-perfect-days.sql (round-count derived from
-- MAX(round_index) instead of hardcoded 5) does NOT apply to existing
-- deployments automatically. Run once, then restart the backend (bootstrap
-- recreates the view and refreshes it):

DROP MATERIALIZED VIEW mv_perfect_days;
