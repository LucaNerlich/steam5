ALTER TABLE seasons ADD CONSTRAINT uq_seasons_dates UNIQUE (start_date, end_date);
