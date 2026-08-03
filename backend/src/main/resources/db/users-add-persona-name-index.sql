-- Index to back the mention-autocomplete search (UserRepository#findTop10By...ContainingIgnoreCase...).
-- NOT managed by Flyway -- apply manually (same convention as other db/*.sql scripts).
-- CONCURRENTLY avoids locking the users table for writes, but cannot run inside a transaction,
-- so this script intentionally has no BEGIN/COMMIT wrapper (same convention as the mv-*.sql scripts).
--
-- Safe to run repeatedly: IF NOT EXISTS makes a re-run a no-op once the index exists and is valid.
-- One edge case IF NOT EXISTS can't fix: if a prior run of this exact statement was interrupted
-- (connection dropped, psql killed) mid-build, Postgres can leave an INVALID index behind under this
-- same name; IF NOT EXISTS then sees the name taken and silently skips rebuilding it. Check for that
-- before assuming this script is a no-op:
--   SELECT indexrelid::regclass, indisvalid FROM pg_index WHERE indexrelid = 'idx_users_persona_name'::regclass;
-- If indisvalid is false, drop it first (also CONCURRENTLY, also outside a transaction), then re-run
-- this file:
--   DROP INDEX CONCURRENTLY idx_users_persona_name;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_persona_name
    ON users (persona_name);
