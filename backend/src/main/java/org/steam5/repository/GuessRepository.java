package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.Guess;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GuessRepository extends JpaRepository<Guess, Long> {
    Optional<Guess> findBySteamIdAndGameDateAndRoundIndex(String steamId, LocalDate date, int roundIndex);

    @Query("select g from Guess g where g.steamId = :steamId and g.gameDate = :date order by g.roundIndex asc")
    List<Guess> findAllForDay(@Param("steamId") String steamId, @Param("date") LocalDate date);
}


