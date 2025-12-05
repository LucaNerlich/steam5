CREATE TABLE seasons (
    id               BIGSERIAL PRIMARY KEY,
    season_number    INTEGER      NOT NULL UNIQUE,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    award_seed       BIGINT       NOT NULL,
    awards_finalized_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT chk_season_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_season_dates ON seasons (start_date, end_date);

CREATE TABLE season_award_results (
    id              BIGSERIAL PRIMARY KEY,
    season_id       BIGINT       NOT NULL REFERENCES seasons (id) ON DELETE CASCADE,
    category        VARCHAR(64)  NOT NULL,
    placement_level INTEGER      NOT NULL,
    steam_id        VARCHAR(32)  NOT NULL,
    metric_value    BIGINT       NOT NULL,
    tiebreak_roll   INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_award_unique UNIQUE (season_id, category, placement_level),
    CONSTRAINT chk_award_level CHECK (placement_level >= 1)
);

CREATE INDEX ix_award_category ON season_award_results (season_id, category);
CREATE INDEX ix_award_steam ON season_award_results (steam_id);


