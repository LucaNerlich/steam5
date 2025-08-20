-- Speed filters like BETWEEN/GTE on total reviews and updated_at min/max
CREATE INDEX IF NOT EXISTS idx_reviews_total_expr ON steam_app_reviews ((total_positive + total_negative));
CREATE INDEX IF NOT EXISTS idx_reviews_updated_at ON steam_app_reviews (updated_at);

-- Joins use app_id + pick_date; also list distinct dates, latest date
CREATE INDEX IF NOT EXISTS idx_pick_app_date ON review_game_pick (app_id, pick_date);
CREATE INDEX IF NOT EXISTS idx_pick_date ON review_game_pick (pick_date);

-- Excluded app lookups by app
CREATE INDEX IF NOT EXISTS idx_excluded_app_id ON excluded_app (app_id);

-- Genres
CREATE INDEX IF NOT EXISTS idx_sag_app   ON steam_app_genre (app_id);
CREATE INDEX IF NOT EXISTS idx_sag_genre ON steam_app_genre (genre_id);

-- Categories
CREATE INDEX IF NOT EXISTS idx_sac_app     ON steam_app_category (app_id);
CREATE INDEX IF NOT EXISTS idx_sac_category ON steam_app_category (category_id);

-- Developers
CREATE INDEX IF NOT EXISTS idx_sad_app       ON steam_app_developer (app_id);
CREATE INDEX IF NOT EXISTS idx_sad_developer ON steam_app_developer (developer_id);

-- Publishers
CREATE INDEX IF NOT EXISTS idx_sap_app       ON steam_app_publisher (app_id);
CREATE INDEX IF NOT EXISTS idx_sap_publisher ON steam_app_publisher (publisher_id);

-- Filter and sort by currency and final price
CREATE INDEX IF NOT EXISTS idx_price_currency_final ON price (currency, "final");

-- Lookups by app and missing blurhash checks
CREATE INDEX IF NOT EXISTS idx_screenshot_app ON screenshots (app_id);

-- Optional: partial index to speed “missing blurhash” scans
CREATE INDEX IF NOT EXISTS idx_screenshot_missing_blurhash
    ON screenshots (id)
    WHERE (blurhash_thumb IS NULL OR blurhash_thumb = '' OR blurhash_full IS NULL OR blurhash_full = '');

CREATE INDEX IF NOT EXISTS idx_developer_name_lower ON developer (lower(name));
CREATE INDEX IF NOT EXISTS idx_publisher_name_lower ON publisher (lower(name));
CREATE INDEX IF NOT EXISTS idx_genre_desc_lower ON genre (lower(description));
CREATE INDEX IF NOT EXISTS idx_category_desc_lower ON category (lower(description));
