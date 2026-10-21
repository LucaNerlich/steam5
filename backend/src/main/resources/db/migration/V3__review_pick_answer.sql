-- Record each pick's answer so a finished day cannot drift when the mutable
-- steam_app_reviews snapshot changes. Existing rows stay null and fall back to
-- the current snapshot (their original answer was never stored); every pick
-- generated from this migration on carries its answer.
ALTER TABLE review_game_pick ADD COLUMN total_reviews integer;
ALTER TABLE review_game_pick ADD COLUMN actual_bucket varchar(32);

COMMENT ON COLUMN review_game_pick.total_reviews IS
    'total positive+negative reviews recorded when the pick was generated; null for legacy rows';
COMMENT ON COLUMN review_game_pick.actual_bucket IS
    'bucket label recorded when the pick was generated; null for legacy rows';
