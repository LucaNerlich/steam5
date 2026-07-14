package org.steam5.game.year;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "year_game_pick", uniqueConstraints = {
        @UniqueConstraint(name = "uq_year_pick_date_app", columnNames = {"pick_date", "app_id"})
})
@NoArgsConstructor
@AllArgsConstructor
public class YearGamePick {

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
}
