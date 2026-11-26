-- Enum columns are validated by the Java enum, not by a database-side whitelist.
-- V1 shipped CHECK constraints generated from the enums as they stood then, and a
-- later constant could only be persisted after a hand-run ALTER in every environment
-- (Hibernate's ddl-auto never widens them). The entities now declare
-- columnDefinition = "varchar(...)", so add nothing here but drop the four staler lists.
ALTER TABLE seasons DROP CONSTRAINT IF EXISTS seasons_status_check;
ALTER TABLE season_award_results DROP CONSTRAINT IF EXISTS season_award_results_category_check;
ALTER TABLE player_spotlights DROP CONSTRAINT IF EXISTS player_spotlights_insight_type_check;
ALTER TABLE comment_reactions DROP CONSTRAINT IF EXISTS comment_reactions_reaction_type_check;
