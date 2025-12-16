TRUNCATE seasons CASCADE;
TRUNCATE season_award_results;
-- if you want ids to start from 1 again:
ALTER SEQUENCE seasons_id_seq RESTART WITH 1;
ALTER SEQUENCE season_award_results_id_seq RESTART WITH 1;
