package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "season_award_results", indexes = {
        @Index(name = "ix_season_award_category", columnList = "season_id,category"),
        @Index(name = "ix_season_award_player", columnList = "steam_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonAwardResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id")
    private Season season;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 64)
    private SeasonAwardCategory category;

    /**
     * Placement level for this award (1 = winner, 2 = runner-up, etc.).
     */
    @Column(name = "placement_level", nullable = false)
    private int placementLevel;

    @Column(name = "steam_id", nullable = false, length = 32)
    private String steamId;

    /**
     * Metric value used to determine winner (e.g., total points or hits).
     */
    @Column(name = "metric_value", nullable = false)
    private long metricValue;

    /**
     * Randomized tie-break roll used (if applicable) so we can audit decisions later.
     */
    @Column(name = "tiebreak_roll")
    private Integer tiebreakRoll;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}


