package org.steam5.game.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceGamePickRepository extends JpaRepository<PriceGamePick, Long> {

    List<PriceGamePick> findByPickDate(LocalDate pickDate);
}
