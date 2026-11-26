-- V2 added uq_seasons_dates (unique, start_date + end_date) while V1's non-unique
-- ix_season_dates stayed behind as an identical btree. The constraint serves every
-- lookup the plain index did, so the duplicate only costs writes and invites a future
-- cleanup to drop the constraint that actually enforces uniqueness.
DROP INDEX IF EXISTS ix_season_dates;
