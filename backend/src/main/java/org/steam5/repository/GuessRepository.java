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

    /**
     * Fetches one user's recent guess history in profile display order.
     * Uses the composite index on (steam_id, game_date, round_index).
     */
    @Query("""
            select g
            from Guess g
            where g.steamId = :steamId
              and g.gameDate between :startDate and :endDate
            order by g.gameDate desc, g.roundIndex asc
            """)
    List<Guess> findRecentBySteamIdOrderByGameDateDescRoundIndexAsc(@Param("steamId") String steamId,
                                                                   @Param("startDate") LocalDate startDate,
                                                                   @Param("endDate") LocalDate endDate);

    /**
     * All players' guesses for one specific (date, round) pair in a single query — used to
     * check every eligible candidate's result on a single shared round (e.g. "yesterday's
     * hardest round") without querying per candidate.
     */
    List<Guess> findByGameDateAndRoundIndex(LocalDate gameDate, int roundIndex);

    /**
     * Per-user daily point totals for BEST_DAY_EVER-style comparisons without materializing
     * every Guess row. Far cheaper than loading full Guess rows for large histories.
     */
    @Query("""
            select g.steamId as steamId,
                   g.gameDate as gameDate,
                   sum(g.points) as totalPoints
            from Guess g
            where g.steamId in :steamIds
            group by g.steamId, g.gameDate
            """)
    List<DailyTotalRow> findDailyTotalsBySteamIdIn(@Param("steamIds") List<String> steamIds);

    interface DailyTotalRow {
        String getSteamId();
        LocalDate getGameDate();
        Long getTotalPoints();
    }

    /**
     * Per-user round counts within a bounded date range, for a specific list of steamIds —
     * used by PlayerSpotlightService's "currently active" eligibility floor. Bounded so a
     * pool of long-dormant all-time-qualified players doesn't require fetching their full
     * (irrelevant) history just to check recent activity.
     */
    List<Guess> findBySteamIdInAndGameDateBetween(List<String> steamIds, LocalDate start, LocalDate end);

    interface UserRoundCountRow {
        String getSteamId();
        Long getRounds();
    }

    /**
     * Round counts per user in a bounded range, without materializing the Guess rows. Used by
     * the spotlight's recency eligibility floor, which only needs the counts.
     */
    @Query("""
            select g.steamId as steamId,
                   count(g) as rounds
            from Guess g
            where g.steamId in :steamIds
              and g.gameDate between :startDate and :endDate
            group by g.steamId
            """)
    List<UserRoundCountRow> countRoundsBySteamIdInAndGameDateBetween(@Param("steamIds") List<String> steamIds,
                                                                     @Param("startDate") LocalDate startDate,
                                                                     @Param("endDate") LocalDate endDate);

    @Query("select g from Guess g where g.steamId = :steamId and g.gameDate = :date order by g.roundIndex asc")
    List<Guess> findAllForDay(@Param("steamId") String steamId, @Param("date") LocalDate date);

    @Query("select g from Guess g where g.steamId = :steamId and g.gameDate between :start and :end order by g.gameDate asc, g.roundIndex asc")
    List<Guess> findBySteamIdBetween(@Param("steamId") String steamId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    @Query("select g from Guess g where g.gameDate = :date")
    List<Guess> findAllByDate(@Param("date") LocalDate date);

    /**
     * Date-range scan used by profile/history and season views.
     * Primarily backed by idx_guesses_game_date.
     */
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
        Long getFlops();
        Long getRounds();
        Long getActiveDays();
    }

    /**
     * Aggregates per-user season metrics over a bounded date range.
     * The date predicate relies on idx_guesses_game_date.
     */
    @Query("""
            select g.steamId as steamId,
                   sum(g.points) as totalPoints,
                   sum(case when g.selectedBucket = g.actualBucket then 1 else 0 end) as hits,
                   sum(case when g.points = 0 then 1 else 0 end) as flops,
                   count(g) as rounds,
                   count(distinct g.gameDate) as activeDays
            from Guess g
            where g.gameDate between :startDate and :endDate
            group by g.steamId
            """)
    List<SeasonStatRow> findSeasonStats(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    interface SeasonLongestStreakRow {
        String getSteamId();
        Integer getLongestStreak();
    }

    /**
     * Longest consecutive-day streak per user within a bounded date range, computed entirely
     * in SQL. Uses idx_guesses_game_date for the range filter and returns one row per user
     * instead of one row per played day.
     */
    @Query(value = """
            with user_dates as (
                select distinct g.steam_id,
                                g.game_date
                from guesses g
                where g.game_date between cast(:startDate as date) and cast(:endDate as date)
            ),
            grouped as (
                select steam_id,
                       game_date,
                       game_date - (row_number() over (partition by steam_id order by game_date))::int as grp
                from user_dates
            ),
            runs as (
                select steam_id,
                       count(*)::int as len
                from grouped
                group by steam_id, grp
            )
            select steam_id as "steamId",
                   max(len) as "longestStreak"
            from runs
            group by steam_id
            """, nativeQuery = true)
    List<SeasonLongestStreakRow> findSeasonLongestStreaks(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);

    /**
     * Current consecutive-day streak per user, computed entirely in SQL. Returns at most one
     * row per user rather than one row per played day: the window function splits each user's
     * distinct dates into consecutive runs, and the final filter keeps only the run that is
     * still active at {@code asOfDate} (ending on that day or the day before).
     *
     * <p>This keeps the result sent back to the JVM proportional to the number of players on
     * the board, not players × days, while preserving the full streak length.</p>
     */
    @Query(value = """
            with user_dates as (
                select distinct g.steam_id,
                                g.game_date
                from guesses g
                where g.steam_id in (:steamIds)
                  and g.game_date <= cast(:asOfDate as date)
            ),
            grouped as (
                select steam_id,
                       game_date,
                       game_date - (row_number() over (partition by steam_id order by game_date))::int as grp
                from user_dates
            ),
            runs as (
                select steam_id,
                       grp,
                       max(game_date) as end_date,
                       count(*)::int as streak
                from grouped
                group by steam_id, grp
            )
            select steam_id as "steamId",
                   streak as "currentStreak"
            from runs
            where end_date >= cast(:asOfDate as date) - 1
            """, nativeQuery = true)
    List<UserStreakRow> findCurrentStreaks(@Param("steamIds") List<String> steamIds,
                                           @Param("asOfDate") LocalDate asOfDate);

    interface UserStreakRow {
        String getSteamId();
        Integer getCurrentStreak();
    }

    interface PlayerStreakRow {
        String getSteamId();
        LocalDate getLastPlayed();
        LocalDate getPreviousPlayed();
        Integer getCurrentStreak();
        Integer getLongestStreak();
    }

    /**
     * Streak and recency facts per user in one row, for callers that previously loaded every
     * played day (spotlight candidate selection) or the whole profile history. The window
     * functions split each user's dates into runs; the extra columns expose the trailing run,
     * the all-time best run, and the two most recent play dates.
     */
    @Query(value = """
            with user_dates as (
                select distinct g.steam_id,
                                g.game_date
                from guesses g
                where g.steam_id in (:steamIds)
                  and g.game_date <= cast(:asOfDate as date)
            ),
            grouped as (
                select steam_id,
                       game_date,
                       game_date - (row_number() over (partition by steam_id order by game_date))::int as grp
                from user_dates
            ),
            runs as (
                select steam_id,
                       grp,
                       max(game_date) as end_date,
                       count(*)::int as len
                from grouped
                group by steam_id, grp
            ),
            ranked_dates as (
                select steam_id,
                       game_date,
                       row_number() over (partition by steam_id order by game_date desc) as rn
                from user_dates
            )
            select d.steam_id as "steamId",
                   last_date.game_date as "lastPlayed",
                   prev_date.game_date as "previousPlayed",
                   case when last_date.game_date >= cast(:asOfDate as date) - 1
                        then coalesce(current_run.len, 0) else 0 end as "currentStreak",
                   coalesce(longest.len, 0) as "longestStreak"
            from (select distinct steam_id from user_dates) d
            left join ranked_dates last_date
                   on last_date.steam_id = d.steam_id and last_date.rn = 1
            left join ranked_dates prev_date
                   on prev_date.steam_id = d.steam_id and prev_date.rn = 2
            left join runs current_run
                   on current_run.steam_id = d.steam_id and current_run.end_date = last_date.game_date
            left join (
                select steam_id, max(len) as len from runs group by steam_id
            ) longest on longest.steam_id = d.steam_id
            """, nativeQuery = true)
    List<PlayerStreakRow> findPlayerStreakStats(@Param("steamIds") List<String> steamIds,
                                                @Param("asOfDate") LocalDate asOfDate);

    // Average submission time per user (in minutes since midnight)
    interface AvgTimeRow {
        String getSteamId();
        Double getAvgMinutes();
        Long getRounds();
    }

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM (created_at AT TIME ZONE 'UTC')) * 60 + EXTRACT(MINUTE FROM (created_at AT TIME ZONE 'UTC'))) AS avgMinutes " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes ASC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeAsc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM (created_at AT TIME ZONE 'UTC')) * 60 + EXTRACT(MINUTE FROM (created_at AT TIME ZONE 'UTC'))) AS avgMinutes " +
            "FROM guesses WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes ASC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeAscInRange(@Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate,
                                                             @Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM (created_at AT TIME ZONE 'UTC')) * 60 + EXTRACT(MINUTE FROM (created_at AT TIME ZONE 'UTC'))) AS avgMinutes " +
            "FROM guesses GROUP BY steam_id HAVING COUNT(*) >= :minRounds " +
            "ORDER BY avgMinutes DESC", nativeQuery = true)
    List<AvgTimeRow> findUsersByAvgSubmissionTimeDesc(@Param("minRounds") int minRounds);

    @Query(value = "SELECT steam_id AS steamId, COUNT(*) AS rounds, " +
            "AVG(EXTRACT(HOUR FROM (created_at AT TIME ZONE 'UTC')) * 60 + EXTRACT(MINUTE FROM (created_at AT TIME ZONE 'UTC'))) AS avgMinutes " +
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
    @Query(value = "WITH day_rounds AS (\n" +
            "  SELECT pick_date AS game_date, COUNT(*) AS rounds_per_day\n" +
            "  FROM review_game_pick\n" +
            "  GROUP BY pick_date\n" +
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
    @Query(value = "WITH day_rounds AS (\n" +
            "  SELECT pick_date AS game_date, COUNT(*) AS rounds_per_day\n" +
            "  FROM review_game_pick\n" +
            "  WHERE pick_date BETWEEN :startDate AND :endDate\n" +
            "  GROUP BY pick_date\n" +
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
            "HAVING COUNT(DISTINCT steam_id) >= 5 " +
            "ORDER BY avgScore DESC", nativeQuery = true)
    List<DailyAvgScoreRow> findDailyAvgScoresDesc();

    @Query(value = "SELECT game_date AS gameDate, AVG(points) AS avgScore, COUNT(DISTINCT steam_id) AS playerCount " +
            "FROM guesses " +
            "GROUP BY game_date " +
            "HAVING COUNT(DISTINCT steam_id) >= 5 " +
            "ORDER BY avgScore ASC", nativeQuery = true)
    List<DailyAvgScoreRow> findDailyAvgScoresAsc();

    @Query(value = "SELECT game_date AS gameDate, AVG(points) AS avgScore, COUNT(DISTINCT steam_id) AS playerCount " +
            "FROM guesses " +
            "WHERE game_date BETWEEN :startDate AND :endDate " +
            "GROUP BY game_date " +
            "HAVING COUNT(DISTINCT steam_id) >= 5 " +
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
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

    // Multi-scan CTE query. Cached in service layer today; consider a window-function rewrite if data volume grows.
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

    /**
     * Minimal row shape for the player-spotlight eligibility query. Unlike the leaderboard
     * materialized views it does not need hits/flops/tooHigh/tooLow, so those aggregates are
     * not computed here.
     */
    interface AllTimeStatsRow {
        String getSteamId();
        Long getTotalPoints();
        Long getRounds();
        Double getAvgPoints();
    }

    /**
     * Same metrics as the all-time leaderboard materialized view (see
     * backend/src/main/resources/db/mv-leaderboard-all-time.sql) but filters results to
     * players with at least {@code minRounds} guesses using a HAVING clause. Note that the
     * HAVING condition limits which aggregated groups are returned to Java, but does not
     * avoid scanning or aggregating the full table at the database level.
     */
    @Query(value = """
            SELECT
                g.steam_id                                                          AS steamId,
                SUM(g.points)                                                       AS totalPoints,
                COUNT(*)                                                            AS rounds,
                AVG(g.points)                                                       AS avgPoints
            FROM guesses g
            GROUP BY g.steam_id
            HAVING COUNT(*) >= :minRounds
            """, nativeQuery = true)
    List<AllTimeStatsRow> aggregateAllTimeStatsHavingMinRounds(@Param("minRounds") long minRounds);

    interface UserStatsRow {
        String getSteamId();
        Long getTotalPoints();
        Long getRounds();
        Long getHits();
        Long getFlops();
        Long getTooHigh();
        Long getTooLow();
        Double getAvgPoints();
    }

    /**
     * Full career aggregates for one user, without materializing every Guess row. Mirrors the
     * columns of {@code mv_leaderboard_all_time} so the profile endpoint stays exact even
     * between materialized-view refreshes.
     */
    @Query(value = """
            SELECT g.steam_id AS "steamId",
                   SUM(g.points) AS "totalPoints",
                   COUNT(*) AS "rounds",
                   SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS "hits",
                   SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END) AS "flops",
                   SUM(CASE WHEN
                       CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) >
                       CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                   THEN 1 ELSE 0 END) AS "tooHigh",
                   SUM(CASE WHEN
                       CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) <
                       CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                   THEN 1 ELSE 0 END) AS "tooLow",
                   AVG(g.points) AS "avgPoints"
            FROM guesses g
            WHERE g.steam_id = :steamId
            GROUP BY g.steam_id
            """, nativeQuery = true)
    List<UserStatsRow> findAllTimeStatsForUser(@Param("steamId") String steamId);
}
