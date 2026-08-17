-- One-time repair for databases that accumulated duplicate award rows before
-- the uq_season_award_result unique constraint existed (concurrent finalizers).
--
-- Hibernate's ddl-auto:update adds uq_season_award_result on startup; if that
-- fails because duplicates already exist, run this script manually (keeping the
-- LOWEST id row per (season_id, category, placement_level)) and restart:

DELETE FROM season_award_results a
USING season_award_results b
WHERE a.id > b.id
  AND a.season_id = b.season_id
  AND a.category = b.category
  AND a.placement_level = b.placement_level;
