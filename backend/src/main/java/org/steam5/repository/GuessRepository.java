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

    @Query("select u.steamId as steamId, coalesce(u.personaName, u.steamId) as personaName, sum(g.points) as totalPoints, count(g) as rounds " +
            "from Guess g join User u on u.steamId = g.steamId " +
            "where g.gameDate = :date group by u.steamId, u.personaName order by sum(g.points) desc")
    List<LeaderboardRow> leaderboardForDay(@Param("date") LocalDate date);

    @Query("select u.steamId as steamId, coalesce(u.personaName, u.steamId) as personaName, sum(g.points) as totalPoints, count(g) as rounds " +
            "from Guess g join User u on u.steamId = g.steamId " +
            "group by u.steamId, u.personaName order by sum(g.points) desc")
    List<LeaderboardRow> leaderboardAllTime();

    @Query("select g from Guess g where g.gameDate = :date")
    List<Guess> findAllByDate(@Param("date") LocalDate date);

    @Query("select g from Guess g where g.gameDate between :start and :end")
    List<Guess> findAllBetween(@Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query("select distinct g.gameDate from Guess g where g.steamId = :steamId and g.gameDate <= :asOfDate order by g.gameDate desc")
    List<LocalDate> findDistinctDatesUpTo(@Param("steamId") String steamId,
                                          @Param("asOfDate") LocalDate asOfDate);

    // Average submission time per user (in minutes since midnight)
    interface AvgTimeRow {
        String getSteamId();
        Double getAvgMinutes();
        Long getRounds();
    }

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM created_at) * 60 + EXTRACT(MINUTE FROM created_at)) AS avgMinutes " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes ASC LIMIT :limit", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeAsc(@Param("minRounds") int minRounds,
                                                     @Param("limit") int limit);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM created_at) * 60 + EXTRACT(MINUTE FROM created_at)) AS avgMinutes " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes DESC LIMIT :limit", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeDesc(@Param("minRounds") int minRounds,
                                                      @Param("limit") int limit);

    // Highest average points per guess
    interface AvgPointsRow {
        String getSteamId();
        Double getAvgPoints();
        Long getRounds();
    }

    @Query(value = "SELECT steam_id AS steamId, AVG(points) AS avgPoints, COUNT(*) AS rounds " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgPoints DESC, rounds DESC, steam_id ASC LIMIT :limit", nativeQuery = true)
    List<AvgPointsRow> findUsersByAvgPointsDesc(@Param("minRounds") int minRounds,
                                                @Param("limit") int limit);

    // Most perfect rounds (points = 5)
    interface PerfectRoundsRow {
        String getSteamId();
        Long getPerfects();
        Long getRounds();
    }

    @Query(value = "SELECT steam_id AS steamId, " +
            "SUM(CASE WHEN points = 5 THEN 1 ELSE 0 END) AS perfects, COUNT(*) AS rounds " +
            "FROM guesses GROUP BY steam_id " +
            "HAVING COUNT(*) >= :minRounds AND SUM(CASE WHEN points = 5 THEN 1 ELSE 0 END) > 0 " +
            "ORDER BY perfects DESC, rounds DESC, steam_id ASC LIMIT :limit", nativeQuery = true)
    List<PerfectRoundsRow> findUsersByPerfectRoundsDesc(@Param("minRounds") int minRounds,
                                                        @Param("limit") int limit);

    // Most perfect days (sum of points equals 5 * rounds_per_day)
    interface PerfectDaysRow {
        String getSteamId();
        Long getPerfectDays();
        Long getRounds();
    }

    @Query(value = "WITH day_rounds AS (\n" +
            "  SELECT game_date, MAX(round_index) AS rounds_per_day\n" +
            "  FROM guesses\n" +
            "  GROUP BY game_date\n" +
            "), user_day AS (\n" +
            "  SELECT g.steam_id, g.game_date, SUM(g.points) AS day_points, MAX(dr.rounds_per_day) AS rounds_per_day\n" +
            "  FROM guesses g JOIN day_rounds dr USING (game_date)\n" +
            "  GROUP BY g.steam_id, g.game_date\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses GROUP BY steam_id\n" +
            "), perfects AS (\n" +
            "  SELECT steam_id, COUNT(*) AS perfect_days\n" +
            "  FROM user_day\n" +
            "  WHERE day_points = 5 * rounds_per_day\n" +
            "  GROUP BY steam_id\n" +
            ")\n" +
            "SELECT p.steam_id AS steamId, p.perfect_days AS perfectDays, uc.rounds AS rounds\n" +
            "FROM perfects p JOIN user_counts uc ON uc.steam_id = p.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY p.perfect_days DESC, uc.rounds DESC, p.steam_id ASC\n" +
            "LIMIT :limit", nativeQuery = true)
    List<PerfectDaysRow> findUsersByPerfectDaysDesc(@Param("minRounds") int minRounds,
                                                    @Param("limit") int limit);

    interface LeaderboardRow {
        String getSteamId();

        String getPersonaName();

        Long getTotalPoints();

        Long getRounds();
    }
}


