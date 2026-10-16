-- One-time cleanup for databases that still carry the legacy Flyway history from
-- the first (later disabled) Flyway era -- August 2025, git a78b272.
--
-- Background: the original Flyway setup applied V1__create_initial_schema.sql and
-- V2__add_ingest_state_table.sql and recorded them in `flyway_schema_history`.
-- Flyway was then disabled and the migration files deleted, so a Flyway-enabled
-- build cannot reconcile that history. Flyway is reintroduced with
-- `baseline-on-migrate` at version 1, which requires the stale table to be gone
-- (otherwise Flyway treats the old history as real and fails validation).
--
-- Safety: the table is only dropped when it matches the known legacy fingerprint
-- (a V1 'create initial schema' row and no BASELINE row). Anything else raises an
-- exception and changes nothing.
--
-- Usage (once per affected database, before starting the Flyway-enabled build):
--   psql -v ON_ERROR_STOP=1 -d <database> -f flyway-legacy-history-reset.sql

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE NOTICE 'No flyway_schema_history table found - nothing to do.';
        RETURN;
    END IF;

    IF EXISTS (
            SELECT 1 FROM public.flyway_schema_history
            WHERE version = '1'
              AND description = 'create initial schema'
              AND script = 'V1__create_initial_schema.sql'
        )
        AND NOT EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE type = 'BASELINE'
        )
    THEN
        DROP TABLE public.flyway_schema_history;
        RAISE NOTICE 'Dropped legacy flyway_schema_history; Flyway will baseline at version 1 on the next start.';
    ELSE
        RAISE EXCEPTION 'flyway_schema_history does not match the legacy fingerprint (or already has a BASELINE row); refusing to drop it.';
    END IF;
END
$$;

COMMIT;
