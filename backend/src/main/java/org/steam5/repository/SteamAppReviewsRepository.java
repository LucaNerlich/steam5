package org.steam5.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.SteamAppReviews;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SteamAppReviewsRepository extends JpaRepository<SteamAppReviews, Long> {

    @Query(value = "SELECT CAST(percentile_disc(0.25) WITHIN GROUP (ORDER BY (total_positive + total_negative)) AS INTEGER) AS lowThreshold, " +
            "CAST(percentile_disc(0.90) WITHIN GROUP (ORDER BY (total_positive + total_negative)) AS INTEGER) AS highThreshold " +
            "FROM steam_app_reviews", nativeQuery = true)
    ReviewThresholds findPercentileThresholds();

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "WHERE p.app_id IS NULL AND (r.total_positive + r.total_negative) <= :threshold " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomLowAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   @Param("threshold") int threshold,
                                   Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "WHERE p.app_id IS NULL AND (r.total_positive + r.total_negative) >= :threshold " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomHighAppIds(@Param("sinceDate") LocalDate sinceDate,
                                    @Param("threshold") int threshold,
                                    Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "WHERE p.app_id IS NULL " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomAnyAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   Pageable pageable);

    interface ReviewThresholds {
        Integer getLowThreshold();

        Integer getHighThreshold();
    }
}


