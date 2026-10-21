package org.steam5.domain;

/**
 * Identifies which leaderboard materialized view a refresh/job/freshness-tracking
 * operation applies to. Shared by LeaderboardRefreshJob (JobDataMap dispatch),
 * LeaderboardRefreshService, and LeaderboardRefreshState.
 */
public enum LeaderboardType {
    ALL_TIME("mv_leaderboard_all_time"),
    MONTHLY("mv_leaderboard_monthly"),
    WEEKLY("mv_leaderboard_weekly"),
    SEASON("mv_leaderboard_season"),
    HARDEST_GAMES("mv_hardest_games"),
    PERFECT_DAYS("mv_perfect_days");

    private final String viewName;

    LeaderboardType(String viewName) {
        this.viewName = viewName;
    }

    public String viewName() {
        return viewName;
    }
}
