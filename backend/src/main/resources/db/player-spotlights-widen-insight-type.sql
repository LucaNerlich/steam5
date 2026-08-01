-- Widen player_spotlights.insight_type after adding TOP_COMMENT (and any future enum values).
-- NOT managed by Flyway — apply manually (same convention as other db/*.sql scripts).
--
-- Hibernate ddl-auto: update does NOT regenerate the CHECK constraint created at
-- table-creation time. Drop it so inserts of new enum string values succeed.
-- Optional: re-add an explicit CHECK listing all current enum values if desired.

ALTER TABLE player_spotlights
    DROP CONSTRAINT IF EXISTS player_spotlights_insight_type_check;
