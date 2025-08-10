-- Track ingestion cursors (e.g., last_app_id for Steam app list)
CREATE TABLE IF NOT EXISTS ingest_state
(
    id          TEXT PRIMARY KEY,
    last_app_id BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);


