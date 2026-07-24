package org.steam5.domain;

/**
 * Identifies which leaderboard materialized view a refresh/job/freshness-tracking
 * operation applies to. Shared by LeaderboardRefreshJob (JobDataMap dispatch),
 * LeaderboardRefreshService, and LeaderboardRefreshState.
 */
public enum LeaderboardType {
    ALL_TIME, MONTHLY, WEEKLY, SEASON
}
