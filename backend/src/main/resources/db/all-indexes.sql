-- Consolidated reference of Postgres indexes for this project: every index that needs (or
-- should optionally get) a manual CREATE INDEX in production, plus commented pointers to
-- indexes that already exist through some other mechanism -- so this one file is the single
-- place to check before a manual-apply session against prod.
--
-- NOT managed by Flyway -- apply manually (same convention as other db/*.sql scripts).
-- Every statement below uses CONCURRENTLY + IF NOT EXISTS, so:
--   * it never locks the target table against writes while building,
--   * re-running this whole file, in part or in full, after some/all statements already
--     succeeded is a safe no-op,
--   * CONCURRENTLY cannot run inside a transaction, so this script intentionally has no
--     BEGIN/COMMIT wrapper (same convention as the mv-*.sql scripts) -- apply with a plain
--     `psql -f all-indexes.sql`, not inside a transaction block.
-- One gap IF NOT EXISTS can't cover: an interrupted prior CONCURRENTLY run can leave an
-- INVALID index behind under the same name, which IF NOT EXISTS then silently skips rebuilding.
-- Check before assuming a statement below is a no-op:
--   SELECT indexrelid::regclass, indisvalid FROM pg_index WHERE NOT indisvalid;
-- and DROP INDEX CONCURRENTLY <name> (also outside a transaction) on any hit before re-running.

-- ============================================================================
-- Already created automatically -- listed here for a complete picture only; no action needed.
-- ============================================================================
-- * Hibernate (spring.jpa.hibernate.ddl-auto: update, active in every profile -- see
--   application.yml) auto-creates these from @Table(indexes = ...) on the owning entity, on
--   every app startup:
--     ix_guess_steam_date_round   UNIQUE (guesses: steam_id, game_date, round_index)
--     idx_comments_game_date      (comments: game_date, created_at DESC)
--     ix_season_award_category    (season_award_results: season_id, category)
--     ix_season_award_player      (season_award_results: steam_id)
--     ux_season_number            UNIQUE (seasons: season_number)
--     ix_season_dates             (seasons: start_date, end_date)
--   Plus named/implicit unique constraints from @UniqueConstraint (users.steam_id,
--   review_game_pick(pick_date, app_id) as uq_review_pick_date_app,
--   comment_reactions(comment_id, steam_id, reaction_type) as uq_comment_reaction, and the
--   single-column unique constraints on details.Developer/Genre/Category/Publisher).
-- * The 6 materialized-view unique indexes required for REFRESH ... CONCURRENTLY
--   (ux_mv_leaderboard_all_time_steam_id, ux_mv_leaderboard_monthly_steam_id,
--   ux_mv_leaderboard_weekly_steam_id, ux_mv_leaderboard_season_steam_id,
--   ux_mv_hardest_games_app_id, ux_mv_perfect_days_entry) are auto-bootstrapped at app startup
--   by LeaderboardMvBootstrapConfig from their own mv-*.sql files -- not duplicated here, since
--   that would create a second, driftable source of truth for DDL the app itself owns.

-- ============================================================================
-- Explicitly flagged for manual/CONCURRENTLY handling on large tables
-- ============================================================================

-- Hibernate creates this non-concurrently via ddl-auto; on a large `guesses` table, rebuild it
-- CONCURRENTLY during a maintenance window instead (see the NOTE on Guess.java's @Table).
-- Backs findAllBetween/findSeasonStats/findSeasonDates (see README's Query Performance Notes).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guesses_game_date
    ON guesses (game_date);

-- Backs the @mention-autocomplete search (UserRepository#findTop10By...ContainingIgnoreCase...,
-- GET /api/users/search). Not auto-created by anything else.
--
-- A plain B-tree can't serve this: ContainingIgnoreCase compiles to
-- `WHERE UPPER(persona_name) LIKE UPPER('%q%')` -- a leading-wildcard LIKE on an expression, not
-- the raw column. pg_trgm's GIN index support recognizes the LIKE operator applied to whatever
-- expression it indexes (leading wildcard included), so indexing UPPER(persona_name) directly
-- matches the query Hibernate actually generates, with no Java/repository change needed.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_persona_name_trgm
    ON users USING gin (UPPER(persona_name) gin_trgm_ops);

-- Optional DBA enhancement for very large review datasets (see SteamAppReviewsRepository's
-- random-pick queries, which anti-join against "eligible" apps via NOT EXISTS). Partial index --
-- standard JPA @Index cannot express a WHERE clause, which is why this isn't an entity index.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_eligible
    ON steam_app_reviews (app_id)
    WHERE (total_positive + total_negative) > 0;

-- ============================================================================
-- Historical indexes -- originally added in V7_add-index.sql (git 7772652), lost when Flyway
-- migrations were removed in favor of Hibernate ddl-auto (git a78b272). Restored here: ddl-auto
-- only manages @Index-annotated entities, so these join/detail tables have had zero indexes
-- since that switch.
-- ============================================================================

-- Speed filters like BETWEEN/GTE on total reviews and updated_at min/max.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_total_expr
    ON steam_app_reviews ((total_positive + total_negative));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_updated_at
    ON steam_app_reviews (updated_at);

-- Joins use app_id + pick_date; also list distinct dates, latest date.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pick_app_date
    ON review_game_pick (app_id, pick_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pick_date
    ON review_game_pick (pick_date);

-- Genres join table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sag_app
    ON steam_app_genre (app_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sag_genre
    ON steam_app_genre (genre_id);

-- Categories join table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sac_app
    ON steam_app_category (app_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sac_category
    ON steam_app_category (category_id);

-- Developers join table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sad_app
    ON steam_app_developer (app_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sad_developer
    ON steam_app_developer (developer_id);

-- Publishers join table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sap_app
    ON steam_app_publisher (app_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sap_publisher
    ON steam_app_publisher (publisher_id);

-- Filter and sort by currency and final price.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_price_currency_final
    ON price (currency, "final");

-- Lookups by app.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_screenshot_app
    ON screenshots (app_id);

-- Partial index to speed "missing blurhash" scans.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_screenshot_missing_blurhash
    ON screenshots (id)
    WHERE (blurhash_thumb IS NULL OR blurhash_thumb = '' OR blurhash_full IS NULL OR blurhash_full = '');

-- ============================================================================
-- Superseded -- safe to run even if never applied; drops leftovers from an earlier version
-- of this file.
-- ============================================================================

-- idx_excluded_app_id (excluded_app.app_id) was briefly listed above, but app_id is that
-- table's @Id (primary key) -- Postgres already auto-creates a unique index on it, so a second
-- index on the same single column is pure dead weight (extra storage, extra write cost on every
-- insert/delete, zero read benefit). Drop it if an earlier run of this file already created it.
DROP INDEX CONCURRENTLY IF EXISTS idx_excluded_app_id;

-- idx_users_persona_name (a plain B-tree on the raw column) was briefly listed above, but it
-- could never actually serve the ContainingIgnoreCase query -- replaced by
-- idx_users_persona_name_trgm above. Drop it if an earlier run of this file already created it.
DROP INDEX CONCURRENTLY IF EXISTS idx_users_persona_name;
