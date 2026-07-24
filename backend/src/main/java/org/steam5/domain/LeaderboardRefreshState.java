package org.steam5.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Tracks when each leaderboard materialized view was last successfully refreshed.
 * An ordinary Hibernate-managed table (unlike the MVs themselves, which are manual/
 * bootstrapped DDL) — ddl-auto creates and maintains this one. Written by
 * LeaderboardRefreshService after each successful refresh; read by
 * LeaderboardController to set the X-Leaderboard-Refreshed-At response header.
 */
@Entity
@Table(name = "leaderboard_refresh_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardRefreshState {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "leaderboard_type", length = 32)
    private LeaderboardType leaderboardType;

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;
}
