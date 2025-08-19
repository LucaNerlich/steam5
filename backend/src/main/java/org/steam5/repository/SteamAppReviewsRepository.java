package org.steam5.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.SteamAppReviews;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SteamAppReviewsRepository extends JpaRepository<SteamAppReviews, Long> {

    @Query(value = "SELECT CAST(percentile_disc(:low) WITHIN GROUP (ORDER BY (total_positive + total_negative)) AS INTEGER) AS lowThreshold, " +
            "CAST(percentile_disc(:high) WITHIN GROUP (ORDER BY (total_positive + total_negative)) AS INTEGER) AS highThreshold " +
            "FROM steam_app_reviews", nativeQuery = true)
    ReviewThresholds findPercentileThresholds(@Param("low") double low, @Param("high") double high);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL AND (r.total_positive + r.total_negative) > 0 AND (r.total_positive + r.total_negative) <= :threshold " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomLowAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   @Param("threshold") int threshold,
                                   Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL AND (r.total_positive + r.total_negative) > 0 AND (r.total_positive + r.total_negative) >= :threshold " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomHighAppIds(@Param("sinceDate") LocalDate sinceDate,
                                    @Param("threshold") int threshold,
                                    Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL AND (r.total_positive + r.total_negative) > 0 " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomAnyAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL AND (r.total_positive + r.total_negative) > :threshold " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomAnyAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   @Param("threshold") int threshold,
                                   Pageable pageable);

    @Query(value = "SELECT COALESCE(SUM(total_positive + total_negative), 0) FROM steam_app_reviews", nativeQuery = true)
    long sumTotalReviews();

    @Query(value = "SELECT COALESCE(MIN(updated_at), now()) FROM steam_app_reviews", nativeQuery = true)
    OffsetDateTime minUpdatedAt();

    @Query(value = "SELECT COALESCE(MAX(updated_at), now()) FROM steam_app_reviews", nativeQuery = true)
    OffsetDateTime maxUpdatedAt();

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL " +
            "AND (r.total_positive + r.total_negative) BETWEEN :lower AND :upper " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomBetween(@Param("sinceDate") LocalDate sinceDate,
                                 @Param("lower") int lower,
                                 @Param("upper") int upper,
                                 Pageable pageable);

    @Query(value = "SELECT r.app_id FROM steam_app_reviews r " +
            "LEFT JOIN review_game_pick p ON p.app_id = r.app_id AND p.pick_date >= :sinceDate " +
            "LEFT JOIN excluded_app x ON x.app_id = r.app_id " +
            "WHERE p.app_id IS NULL AND x.app_id IS NULL " +
            "AND (r.total_positive + r.total_negative) >= :lower " +
            "ORDER BY random()",
            nativeQuery = true)
    List<Long> findRandomGte(@Param("sinceDate") LocalDate sinceDate,
                             @Param("lower") int lower,
                             Pageable pageable);

    @Query(value = "SELECT app_id FROM steam_app_reviews ORDER BY updated_at ASC", nativeQuery = true)
    List<Long> findIdsOrderByUpdatedAtAsc(Pageable pageable);

    interface ReviewThresholds {
        Integer getLowThreshold();

        Integer getHighThreshold();
    }

}


