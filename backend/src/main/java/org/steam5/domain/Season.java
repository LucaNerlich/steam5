package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "seasons", indexes = {
        @Index(name = "ux_season_number", columnList = "season_number", unique = true),
        @Index(name = "ix_season_dates", columnList = "start_date,end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_number", nullable = false, unique = true)
    private int seasonNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SeasonStatus status = SeasonStatus.PLANNED;

    /**
     * Seed used for deterministic tie-breaking within this season's awards.
     */
    @Column(name = "award_seed", nullable = false)
    private long awardSeed;

    @Column(name = "awards_finalized_at")
    private OffsetDateTime awardsFinalizedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}


