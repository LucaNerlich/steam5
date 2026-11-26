package org.steam5.domain;

/**
 * The first seven values (DAY_STREAK, BEST_DAY_EVER, BEAT_THE_ODDS, WELCOME_BACK,
 * MOST_IMPROVED, HOT_STREAK, TOP_COMMENT) form {@link org.steam5.service.PlayerSpotlightService}'s
 * "competitive pool": every one of them that has at least one qualifying candidate on a
 * given day is entered into a uniform-random lottery (NOT a priority ladder —
 * the enum declaration order only matters as the fixed order in which each
 * tier's qualification is evaluated, not as a preference ranking).
 * WEEKLY_ACHIEVEMENT and MILESTONE are sequential fallbacks used only when
 * the competitive pool has zero qualifying tiers for the day.
 * <p>
 * Persisted as {@code @Enumerated(EnumType.STRING)} on
 * {@link PlayerSpotlight#getInsightType()}. The column is plain
 * {@code varchar(32)} (see {@link PlayerSpotlight}); V5 dropped the generated CHECK
 * constraint that used to be the real whitelist, so adding a constant needs no migration.
 * Hibernate still adds that check to tables it creates itself.
 */
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    BEAT_THE_ODDS,
    WELCOME_BACK,
    MOST_IMPROVED,
    HOT_STREAK,
    TOP_COMMENT,
    WEEKLY_ACHIEVEMENT,
    MILESTONE
}
