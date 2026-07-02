package org.steam5.domain;

/**
 * Ordered by "how good a story is this" — index 0 is the most notable.
 * PlayerSpotlightService picks the best-populated tier each day.
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
