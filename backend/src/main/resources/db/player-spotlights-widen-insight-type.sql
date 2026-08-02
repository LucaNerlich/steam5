-- Widen player_spotlights.insight_type after adding TOP_COMMENT (and keep CHECK in sync).
-- NOT managed by Flyway — apply manually (same convention as other db/*.sql scripts).
--
-- Hibernate ddl-auto: update does NOT regenerate the CHECK constraint created at
-- table-creation time. Drop the stale CHECK, then re-add one listing every current
-- PlayerSpotlightInsightType value (must match the Java enum).

BEGIN;

ALTER TABLE player_spotlights
    DROP CONSTRAINT IF EXISTS player_spotlights_insight_type_check;

ALTER TABLE player_spotlights
    ADD CONSTRAINT player_spotlights_insight_type_check
        CHECK (insight_type IN (
            'DAY_STREAK',
            'BEST_DAY_EVER',
            'BEAT_THE_ODDS',
            'WELCOME_BACK',
            'MOST_IMPROVED',
            'HOT_STREAK',
            'TOP_COMMENT',
            'WEEKLY_ACHIEVEMENT',
            'MILESTONE'
        ));

COMMIT;
