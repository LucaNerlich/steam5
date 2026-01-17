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

    @Query("select min(g.gameDate) from Guess g")
    Optional<LocalDate> findEarliestGameDate();

    @Query("select max(g.gameDate) from Guess g")
    Optional<LocalDate> findLatestGameDate();

    interface SeasonStatRow {
        String getSteamId();
        Long getTotalPoints();
        Long getHits();
        Long getRounds();
        Long getActiveDays();
    }

    @Query("""
            select g.steamId as steamId,
                   sum(g.points) as totalPoints,
                   sum(case when g.selectedBucket = g.actualBucket then 1 else 0 end) as hits,
                   count(g) as rounds,
                   count(distinct g.gameDate) as activeDays
            from Guess g
            where g.gameDate between :startDate and :endDate
            group by g.steamId
            """)
    List<SeasonStatRow> findSeasonStats(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    interface SeasonDateRow {
        String getSteamId();
        LocalDate getGameDate();
    }

    @Query("""
            select g.steamId as steamId,
                   g.gameDate as gameDate
            from Guess g
            where g.gameDate between :startDate and :endDate
            order by g.steamId asc, g.gameDate asc
            """)
    List<SeasonDateRow> findSeasonDates(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Query("select distinct g.gameDate from Guess g where g.steamId = :steamId and g.gameDate <= :asOfDate order by g.gameDate desc")
    List<LocalDate> findDistinctDatesUpTo(@Param("steamId") String steamId,
                                          @Param("asOfDate") LocalDate asOfDate);

    interface UserDateRow {
        String getSteamId();
        LocalDate getGameDate();
    }

    @Query("""
            select distinct g.steamId as steamId,
                   g.gameDate as gameDate
            from Guess g
            where g.steamId in :steamIds
              and g.gameDate <= :asOfDate
            order by g.steamId asc, g.gameDate desc
            """)
    List<UserDateRow> findDistinctDatesUpToForUsers(@Param("steamIds") List<String> steamIds,
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
            "ORDER BY avgMinutes ASC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeAsc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM created_at) * 60 + EXTRACT(MINUTE FROM created_at)) AS avgMinutes " +
            "FROM guesses WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes ASC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeAscInRange(@Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate,
                                                             @Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM created_at) * 60 + EXTRACT(MINUTE FROM created_at)) AS avgMinutes " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes DESC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeDesc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM created_at) * 60 + EXTRACT(MINUTE FROM created_at)) AS avgMinutes " +
            "FROM guesses WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes DESC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeDescInRange(@Param("startDate") LocalDate startDate,
                                                              @Param("endDate") LocalDate endDate,
                                                              @Param("minRounds") int minRounds);

    // Highest average points per guess
    interface AvgPointsRow {
        String getSteamId();
        Double getAvgPoints();
        Long getRounds();
    }

    @Query(value = "SELECT steam_id AS steamId, AVG(points) AS avgPoints, COUNT(*) AS rounds " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgPoints DESC, rounds DESC, steam_id ASC", nativeQuery = true)
    List<AvgPointsRow> findUsersByAvgPointsDesc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, AVG(points) AS avgPoints, COUNT(*) AS rounds " +
            "FROM guesses WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgPoints DESC, rounds DESC, steam_id ASC", nativeQuery = true)
    List<AvgPointsRow> findUsersByAvgPointsDescInRange(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate,
                                                        @Param("minRounds") int minRounds);

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
            "ORDER BY perfects DESC, rounds DESC, steam_id ASC", nativeQuery = true)
    List<PerfectRoundsRow> findUsersByPerfectRoundsDesc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, " +
            "SUM(CASE WHEN points = 5 THEN 1 ELSE 0 END) AS perfects, COUNT(*) AS rounds " +
            "FROM guesses WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY steam_id " +
            "HAVING COUNT(*) >= :minRounds AND SUM(CASE WHEN points = 5 THEN 1 ELSE 0 END) > 0 " +
            "ORDER BY perfects DESC, rounds DESC, steam_id ASC", nativeQuery = true)
    List<PerfectRoundsRow> findUsersByPerfectRoundsDescInRange(@Param("startDate") LocalDate startDate,
                                                                @Param("endDate") LocalDate endDate,
                                                                @Param("minRounds") int minRounds);

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
            "ORDER BY p.perfect_days DESC, uc.rounds DESC, p.steam_id ASC"
            , nativeQuery = true)
    List<PerfectDaysRow> findUsersByPerfectDaysDesc(@Param("minRounds") int minRounds);

    @Query(value = "WITH day_rounds AS (\n" +
            "  SELECT game_date, MAX(round_index) AS rounds_per_day\n" +
            "  FROM guesses\n" +
            "  WHERE game_date BETWEEN :startDate AND :endDate\n" +
            "  GROUP BY game_date\n" +
            "), user_day AS (\n" +
            "  SELECT g.steam_id, g.game_date, SUM(g.points) AS day_points, MAX(dr.rounds_per_day) AS rounds_per_day\n" +
            "  FROM guesses g JOIN day_rounds dr USING (game_date)\n" +
            "  WHERE g.game_date BETWEEN :startDate AND :endDate\n" +
            "  GROUP BY g.steam_id, g.game_date\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses WHERE game_date BETWEEN :startDate AND :endDate GROUP BY steam_id\n" +
            "), perfects AS (\n" +
            "  SELECT steam_id, COUNT(*) AS perfect_days\n" +
            "  FROM user_day\n" +
            "  WHERE day_points = 5 * rounds_per_day\n" +
            "  GROUP BY steam_id\n" +
            ")\n" +
            "SELECT p.steam_id AS steamId, p.perfect_days AS perfectDays, uc.rounds AS rounds\n" +
            "FROM perfects p JOIN user_counts uc ON uc.steam_id = p.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY p.perfect_days DESC, uc.rounds DESC, p.steam_id ASC"
            , nativeQuery = true)
    List<PerfectDaysRow> findUsersByPerfectDaysDescInRange(@Param("startDate") LocalDate startDate,
                                                            @Param("endDate") LocalDate endDate,
                                                            @Param("minRounds") int minRounds);

    // Daily average scores
    interface DailyAvgScoreRow {
        LocalDate getGameDate();
        Double getAvgScore();
        Long getPlayerCount();
    }

    interface RoundAvgScoreRow {
        LocalDate getGameDate();
        Integer getRoundIndex();
        Long getAppId();
        Double getAvgScore();
        Long getPlayerCount();
        String getAppName();
    }

    @Query(value = "SELECT game_date AS gameDate, AVG(points) AS avgScore, COUNT(DISTINCT steam_id) AS playerCount " +
            "FROM guesses " +
            "GROUP BY game_date " +
            "HAVING COUNT(*) >= 5 " +
            "ORDER BY avgScore DESC", nativeQuery = true)
    List<DailyAvgScoreRow> findDailyAvgScoresDesc();

    @Query(value = "SELECT game_date AS gameDate, AVG(points) AS avgScore, COUNT(DISTINCT steam_id) AS playerCount " +
            "FROM guesses " +
            "GROUP BY game_date " +
            "HAVING COUNT(*) >= 5 " +
            "ORDER BY avgScore ASC", nativeQuery = true)
    List<DailyAvgScoreRow> findDailyAvgScoresAsc();

    @Query(value = "SELECT game_date AS gameDate, AVG(points) AS avgScore, COUNT(DISTINCT steam_id) AS playerCount " +
            "FROM guesses " +
            "WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY game_date " +
            "HAVING COUNT(*) >= 5 " +
            "ORDER BY game_date ASC", nativeQuery = true)
    List<DailyAvgScoreRow> findDailyAvgScoresInRange(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    @Query(value = "SELECT g.game_date AS gameDate, " +
            "g.round_index AS roundIndex, " +
            "g.app_id AS appId, " +
            "AVG(g.points) AS avgScore, " +
            "COUNT(DISTINCT g.steam_id) AS playerCount, " +
            "COALESCE(MAX(sai.name), CAST(g.app_id AS TEXT)) AS appName " +
            "FROM guesses g " +
            "LEFT JOIN steam_app_index sai ON sai.app_id = g.app_id " +
            "WHERE g.game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY g.game_date, g.round_index, g.app_id " +
            "HAVING COUNT(*) >= 5", nativeQuery = true)
    List<RoundAvgScoreRow> findRoundAvgScoresInRange(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    // Sum of daily time differences (time between first and last guess per day)
    interface DailyTimeDiffRow {
        String getSteamId();
        Long getTotalSeconds();  // Sum of daily time differences in seconds
        Long getRounds();
    }

    @Query(value = "WITH daily_diffs AS (\n" +
            "  SELECT steam_id, game_date,\n" +
            "    EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at))) AS diff_seconds\n" +
            "  FROM guesses\n" +
            "  GROUP BY steam_id, game_date\n" +
            "  HAVING COUNT(*) >= 2\n" +  // At least 2 guesses per day
            "), user_totals AS (\n" +
            "  SELECT steam_id, SUM(diff_seconds)::BIGINT AS total_seconds\n" +
            "  FROM daily_diffs\n" +
            "  GROUP BY steam_id\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses GROUP BY steam_id\n" +
            ")\n" +
            "SELECT ut.steam_id AS steamId, ut.total_seconds AS totalSeconds, uc.rounds AS rounds\n" +
            "FROM user_totals ut JOIN user_counts uc ON uc.steam_id = ut.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY ut.total_seconds ASC, uc.rounds DESC, ut.steam_id ASC", nativeQuery = true)
    List<DailyTimeDiffRow> findUsersByDailyTimeDiffAsc(@Param("minRounds") int minRounds);

    @Query(value = "WITH daily_diffs AS (\n" +
            "  SELECT steam_id, game_date,\n" +
            "    EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at))) AS diff_seconds\n" +
            "  FROM guesses\n" +
            "  WHERE game_date BETWEEN :startDate AND :endDate\n" +
            "  GROUP BY steam_id, game_date\n" +
            "  HAVING COUNT(*) >= 2\n" +  // At least 2 guesses per day
            "), user_totals AS (\n" +
            "  SELECT steam_id, SUM(diff_seconds)::BIGINT AS total_seconds\n" +
            "  FROM daily_diffs\n" +
            "  GROUP BY steam_id\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses WHERE game_date BETWEEN :startDate AND :endDate GROUP BY steam_id\n" +
            ")\n" +
            "SELECT ut.steam_id AS steamId, ut.total_seconds AS totalSeconds, uc.rounds AS rounds\n" +
            "FROM user_totals ut JOIN user_counts uc ON uc.steam_id = ut.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY ut.total_seconds ASC, uc.rounds DESC, ut.steam_id ASC", nativeQuery = true)
    List<DailyTimeDiffRow> findUsersByDailyTimeDiffAscInRange(@Param("startDate") LocalDate startDate,
                                                               @Param("endDate") LocalDate endDate,
                                                               @Param("minRounds") int minRounds);

    @Query(value = "WITH daily_diffs AS (\n" +
            "  SELECT steam_id, game_date,\n" +
            "    EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at))) AS diff_seconds\n" +
            "  FROM guesses\n" +
            "  GROUP BY steam_id, game_date\n" +
            "  HAVING COUNT(*) >= 2\n" +  // At least 2 guesses per day
            "), user_totals AS (\n" +
            "  SELECT steam_id, SUM(diff_seconds)::BIGINT AS total_seconds\n" +
            "  FROM daily_diffs\n" +
            "  GROUP BY steam_id\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses GROUP BY steam_id\n" +
            ")\n" +
            "SELECT ut.steam_id AS steamId, ut.total_seconds AS totalSeconds, uc.rounds AS rounds\n" +
            "FROM user_totals ut JOIN user_counts uc ON uc.steam_id = ut.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY ut.total_seconds DESC, uc.rounds DESC, ut.steam_id ASC", nativeQuery = true)
    List<DailyTimeDiffRow> findUsersByDailyTimeDiffDesc(@Param("minRounds") int minRounds);

    @Query(value = "WITH daily_diffs AS (\n" +
            "  SELECT steam_id, game_date,\n" +
            "    EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at))) AS diff_seconds\n" +
            "  FROM guesses\n" +
            "  WHERE game_date BETWEEN :startDate AND :endDate\n" +
            "  GROUP BY steam_id, game_date\n" +
            "  HAVING COUNT(*) >= 2\n" +  // At least 2 guesses per day
            "), user_totals AS (\n" +
            "  SELECT steam_id, SUM(diff_seconds)::BIGINT AS total_seconds\n" +
            "  FROM daily_diffs\n" +
            "  GROUP BY steam_id\n" +
            "), user_counts AS (\n" +
            "  SELECT steam_id, COUNT(*) AS rounds FROM guesses WHERE game_date BETWEEN :startDate AND :endDate GROUP BY steam_id\n" +
            ")\n" +
            "SELECT ut.steam_id AS steamId, ut.total_seconds AS totalSeconds, uc.rounds AS rounds\n" +
            "FROM user_totals ut JOIN user_counts uc ON uc.steam_id = ut.steam_id\n" +
            "WHERE uc.rounds >= :minRounds\n" +
            "ORDER BY ut.total_seconds DESC, uc.rounds DESC, ut.steam_id ASC", nativeQuery = true)
    List<DailyTimeDiffRow> findUsersByDailyTimeDiffDescInRange(@Param("startDate") LocalDate startDate,
                                                                @Param("endDate") LocalDate endDate,
                                                                @Param("minRounds") int minRounds);

    interface LeaderboardRow {
        String getSteamId();

        String getPersonaName();

        Long getTotalPoints();

        Long getRounds();
    }
}


