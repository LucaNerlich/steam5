-- Initial schema for Steam5 Review Guesser
-- PostgreSQL-compatible

-- Master index of all Steam apps fetched from the large list API
CREATE TABLE IF NOT EXISTS steam_app_index
(
    app_id BIGINT PRIMARY KEY,
    name   TEXT NOT NULL
);

