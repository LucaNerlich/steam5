-- Perfect-day rounds now come from the generated picks (review_game_pick), not
-- from the highest attempted round in guesses (#622). LeaderboardMvBootstrapConfig
-- creates the view from db/mv-perfect-days.sql only when it is absent, so drop it
-- here; the next startup recreates it with the corrected HAVING clause and
-- repopulates it.
DROP MATERIALIZED VIEW IF EXISTS mv_perfect_days;
