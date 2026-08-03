-- Index to back the mention-autocomplete search (UserRepository#findTop10By...ContainingIgnoreCase...).
-- NOT managed by Flyway -- apply manually (same convention as other db/*.sql scripts).
-- CONCURRENTLY avoids locking the users table for writes, but cannot run inside a transaction,
-- so this script intentionally has no BEGIN/COMMIT wrapper (same convention as the mv-*.sql scripts).

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_persona_name
    ON users (persona_name);
