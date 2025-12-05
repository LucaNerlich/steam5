package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findBySeasonNumber(int seasonNumber);

    Optional<Season> findTopByOrderBySeasonNumberDesc();

    Optional<Season> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate startInclusive, LocalDate endInclusive);

    List<Season> findAllByStatusOrderBySeasonNumberAsc(SeasonStatus status);

    List<Season> findAllByOrderBySeasonNumberDesc();
}


