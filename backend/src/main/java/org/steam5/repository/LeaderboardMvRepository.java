package org.steam5.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
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
}
