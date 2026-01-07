TRUNCATE TABLE season_award_results;
TRUNCATE TABLE seasons CASCADE;
ALTER SEQUENCE public.season_award_results_id_seq RESTART WITH 1;
ALTER SEQUENCE public.seasons_id_seq RESTART WITH 1;
