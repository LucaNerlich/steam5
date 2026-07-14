package org.steam5.game.year;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "year_game_pick_lock")
public class YearGamePickLock {

    @Id
    @Column(name = "pick_date", nullable = false)
    private LocalDate pickDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
