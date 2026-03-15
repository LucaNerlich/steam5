package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
// NOTE: Hibernate only auto-creates this index when ddl-auto is update/create.
// For large production tables, apply manually during maintenance:
// CREATE INDEX CONCURRENTLY idx_guesses_game_date ON guesses (game_date)
// (CONCURRENTLY cannot be used by Hibernate schema updates because they run in a transaction).
@Table(name = "guesses", indexes = {
        @Index(name = "ix_guess_steam_date_round", columnList = "steam_id, game_date, round_index", unique = true),
        @Index(name = "idx_guesses_game_date", columnList = "game_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Guess {

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

    @Column(name = "selected_bucket", nullable = false)
    private String selectedBucket;

    @Column(name = "actual_bucket", nullable = false)
    private String actualBucket;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}


