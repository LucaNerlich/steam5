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

    /**
     * {@code columnDefinition} is required here: without it, Hibernate 6+ generates its own
     * CHECK constraint enumerating the enum's constants at the time the table is first
     * created — and since {@code ddl-auto: update} never alters existing constraints, adding
     * a new {@link LeaderboardType} constant later (e.g. HARDEST_GAMES) makes every insert of
     * that new value violate the stale, baked-in constraint. Explicitly declaring the column
     * type opts out of that auto-generated constraint entirely, so growing the enum never
     * requires a matching schema migration.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "leaderboard_type", length = 32, columnDefinition = "varchar(32)")
    private LeaderboardType leaderboardType;

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;
}
