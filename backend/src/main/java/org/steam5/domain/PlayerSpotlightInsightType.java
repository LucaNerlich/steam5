package org.steam5.domain;

/**
 * The first six values (DAY_STREAK, BEST_DAY_EVER, BEAT_THE_ODDS, WELCOME_BACK,
 * MOST_IMPROVED, HOT_STREAK) form {@link org.steam5.service.PlayerSpotlightService}'s "competitive
 * pool": every one of them that has at least one qualifying candidate on a
 * given day is entered into a uniform-random lottery (NOT a priority ladder —
 * the enum declaration order only matters as the fixed order in which each
 * tier's qualification is evaluated, not as a preference ranking).
 * WEEKLY_ACHIEVEMENT and MILESTONE are sequential fallbacks used only when
 * the competitive pool has zero qualifying tiers for the day.
 * <p>
 * <b>Production-readiness warning for future maintainers:</b> this enum is
 * persisted via {@code @Enumerated(EnumType.STRING)} on
 * {@link PlayerSpotlight#getInsightType()}. Hibernate auto-generates a
 * Postgres {@code CHECK} constraint on {@code player_spotlights.insight_type}
 * listing this enum's values AT TABLE-CREATION TIME, and this codebase's
 * {@code ddl-auto: update} setting does NOT widen that constraint when this
 * enum grows later (the same limitation affects the enum-backed
 * {@code season_award_results} and {@code seasons} tables). If you add a new
 * value here, you MUST run, in every already-provisioned environment after
 * deploying:
 * <pre>
 * ALTER TABLE player_spotlights DROP CONSTRAINT player_spotlights_insight_type_check;
 * </pre>
 * (or an equivalent widening {@code ALTER}). Skipping this will not fail
 * loudly: {@link org.steam5.job.PlayerSpotlightJob#execute()} catches and
 * merely logs persistence failures, so on any day the new tier wins the
 * lottery, the INSERT will throw a CHECK-constraint violation and the
 * spotlight box will simply disappear from the site that day with no
 * visible error.
 */
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    BEAT_THE_ODDS,
    WELCOME_BACK,
    MOST_IMPROVED,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
