package org.steam5.game.year;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface YearGamePickRepository extends JpaRepository<YearGamePick, Long> {

    List<YearGamePick> findByPickDate(LocalDate pickDate);
}
