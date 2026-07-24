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

    @Query(value = "SELECT ispopulated FROM pg_matviews WHERE matviewname = :viewName", nativeQuery = true)
    Boolean isPopulated(@Param("viewName") String viewName);

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
}
