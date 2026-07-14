package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "year_guesses", indexes = {
        @Index(name = "ix_year_guess_steam_date_round", columnList = "steam_id, game_date, round_index", unique = true),
        @Index(name = "idx_year_guesses_game_date", columnList = "game_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YearGuess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "steam_id", nullable = false, length = 32)
    private String steamId;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "round_index", nullable = false)
    private int roundIndex;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "guessed_year")
    private Integer guessedYear;

    @Column(name = "actual_year", nullable = false)
    private int actualYear;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    @Column(name = "best_distance")
    private Integer bestDistance;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
