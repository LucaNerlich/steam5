package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The "good vibes" player of the day, computed once nightly by PlayerSpotlightJob
 * and read back for round 1 of the following day. One row per game date.
 */
@Entity
@Table(name = "player_spotlights")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSpotlight {

    @Id
    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "steam_id", nullable = false, length = 32)
    private String steamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", nullable = false, length = 32)
    private PlayerSpotlightInsightType insightType;

    @Column(name = "headline", nullable = false, length = 255)
    private String headline;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    @Column(name = "stat_label", length = 64)
    private String statLabel;

    @Column(name = "stat_value")
    private Double statValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
