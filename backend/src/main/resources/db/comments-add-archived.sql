-- Soft-archive columns for review-game comments.
-- NOT managed by Flyway — apply manually (same convention as other db/*.sql scripts).
-- Safe for existing rows: add nullable column, backfill false, then enforce NOT NULL.
-- Wrapped in a single transaction so no intermediate commit can leave a half-migrated column.

BEGIN;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS archived boolean;

UPDATE comments
SET archived = false
WHERE archived IS NULL;

ALTER TABLE comments
    ALTER COLUMN archived SET DEFAULT false;

ALTER TABLE comments
    ALTER COLUMN archived SET NOT NULL;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS archived_at timestamptz;

COMMIT;
