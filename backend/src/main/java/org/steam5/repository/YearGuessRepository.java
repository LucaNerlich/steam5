package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.YearGuess;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface YearGuessRepository extends JpaRepository<YearGuess, Long> {

    Optional<YearGuess> findBySteamIdAndGameDateAndRoundIndex(String steamId, LocalDate date, int roundIndex);

    @Query("select g from YearGuess g where g.steamId = :steamId and g.gameDate = :date order by g.roundIndex asc")
    List<YearGuess> findAllForDay(@Param("steamId") String steamId, @Param("date") LocalDate date);

    @Query("select g from YearGuess g where g.steamId = :steamId and g.gameDate between :start and :end order by g.gameDate asc, g.roundIndex asc")
    List<YearGuess> findBySteamIdBetween(@Param("steamId") String steamId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);
}
