package org.steam5.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.Guess;

import java.util.List;

/**
 * Read access to the leaderboard materialized views (see
 * backend/src/main/resources/db/mv-leaderboard-*.sql). These views are not JPA-managed
 * entities, so this repository extends the plain {@link Repository} marker — every method
 * is a hand-written native query. {@link Guess} is reused only to satisfy the generic bound;
 * none of these methods touch the {@code guesses} table directly.
 */
public interface LeaderboardMvRepository extends Repository<Guess, Long> {

    interface LeaderboardMvRow {
        String getSteamId();
        Long getTotalPoints();
        Long getRounds();
        Long getHits();
        Long getFlops();
        Long getTooHigh();
        Long getTooLow();
        Double getAvgPoints();
        String getPersonaName();
        String getAvatarFull();
        String getBlurdataAvatarFull();
        String getProfileUrl();
    }

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_all_time ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findAllTime();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_monthly ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findMonthly();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_weekly ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findWeekly();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_season ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findSeason();

    interface HardestGameMvRow {
        Long getAppId();
        String getAppName();
        Double getAvgScore();
        Long getPlayerCount();
        Long getTooHighCount();
        Long getTooLowCount();
        Long getTotalGuesses();
        String getMostCommonWrongBucket();
        Long getMostCommonWrongBucketCount();
        String getActualBucket();
        java.time.LocalDate getLatestPickDate();
    }

    @Query(value = "SELECT app_id AS appId, app_name AS appName, avg_score AS avgScore, player_count AS playerCount, " +
            "too_high_count AS tooHighCount, too_low_count AS tooLowCount, total_guesses AS totalGuesses, " +
            "most_common_wrong_bucket AS mostCommonWrongBucket, most_common_wrong_bucket_count AS mostCommonWrongBucketCount, " +
            "actual_bucket AS actualBucket, latest_pick_date AS latestPickDate " +
            "FROM mv_hardest_games ORDER BY avg_score ASC, player_count DESC", nativeQuery = true)
    List<HardestGameMvRow> findHardestGames();

    @Query(value = "SELECT ispopulated FROM pg_matviews WHERE matviewname = :viewName", nativeQuery = true)
    Boolean isPopulated(@Param("viewName") String viewName);

    /**
     * Transaction-scoped advisory lock (auto-released at commit/rollback, no manual unlock
     * needed — safe with pooled connections). Guards each MV's REFRESH against colliding with
     * another session already refreshing the same view: Quartz's {@code @DisallowConcurrentExecution}
     * only prevents concurrent firings within one JVM's scheduler, not across process restarts
     * (an old process's REFRESH transaction can still be finishing when a newly-started
     * process's trigger fires immediately) — the two can otherwise deadlock in Postgres.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    Boolean tryAdvisoryXactLock(@Param("key") long key);

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_all_time", nativeQuery = true)
    void refreshAllTimeFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_all_time", nativeQuery = true)
    void refreshAllTimeConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_monthly", nativeQuery = true)
    void refreshMonthlyFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_monthly", nativeQuery = true)
    void refreshMonthlyConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_weekly", nativeQuery = true)
    void refreshWeeklyFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_weekly", nativeQuery = true)
    void refreshWeeklyConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_hardest_games", nativeQuery = true)
    void refreshHardestGamesFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_hardest_games", nativeQuery = true)
    void refreshHardestGamesConcurrently();

    interface PerfectDayMvRow {
        String getSteamId();
        String getPersonaName();
        java.time.LocalDate getGameDate();
        Long getTotalPoints();
        String getAppNames();
    }

    @Query(value = "SELECT steam_id AS steamId, persona_name AS personaName, game_date AS gameDate, " +
            "total_points AS totalPoints, app_names AS appNames " +
            "FROM mv_perfect_days ORDER BY game_date DESC", nativeQuery = true)
    List<PerfectDayMvRow> findPerfectDays();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_perfect_days", nativeQuery = true)
    void refreshPerfectDaysFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_perfect_days", nativeQuery = true)
    void refreshPerfectDaysConcurrently();
}
