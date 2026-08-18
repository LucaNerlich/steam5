package org.steam5.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "review_game_pick", uniqueConstraints = {
        @UniqueConstraint(name = "uq_review_pick_date_app", columnNames = {"pick_date", "app_id"})
})
@NoArgsConstructor
public class ReviewGamePick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "pick_date", nullable = false)
    private LocalDate pickDate;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Player-visible round order for the day (1..n), assigned after the shuffle
     * so statistics round labels match the order actually served to players.
     * Null for legacy rows, which fall back to createdAt/id ordering.
     */
    @Column(name = "round_index")
    private Integer roundIndex;

    public ReviewGamePick(Long id, LocalDate pickDate, Long appId, OffsetDateTime createdAt) {
        this(id, pickDate, appId, createdAt, null);
    }

    public ReviewGamePick(Long id, LocalDate pickDate, Long appId, OffsetDateTime createdAt, Integer roundIndex) {
        this.id = id;
        this.pickDate = pickDate;
        this.appId = appId;
        this.createdAt = createdAt;
        this.roundIndex = roundIndex;
    }

    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }
}
