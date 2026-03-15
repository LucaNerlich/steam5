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

    // Optional DBA enhancement for very large datasets:
    // CREATE INDEX idx_reviews_eligible ON steam_app_reviews (app_id)
    // WHERE (total_positive + total_negative) > 0;
    // Standard JPA @Index cannot express partial-index WHERE clauses.
    // If Flyway/Liquibase is introduced, prefer managing this index via a migration.
    /**
     * Randomly selects low-review eligible apps via a two-phase CTE.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) > 0
                  AND (r.total_positive + r.total_negative) <= :threshold
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomLowAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   @Param("threshold") int threshold,
                                   Pageable pageable);

    /**
     * Randomly selects high-review eligible apps via a two-phase CTE.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) > 0
                  AND (r.total_positive + r.total_negative) >= :threshold
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomHighAppIds(@Param("sinceDate") LocalDate sinceDate,
                                    @Param("threshold") int threshold,
                                    Pageable pageable);

    /**
     * Randomly selects any eligible app with at least one review.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) > 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomAnyAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   Pageable pageable);

    /**
     * Randomly selects eligible apps above a dynamic threshold.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) > :threshold
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomAnyAppIds(@Param("sinceDate") LocalDate sinceDate,
                                   @Param("threshold") int threshold,
                                   Pageable pageable);

    @Query(value = "SELECT COALESCE(SUM(total_positive + total_negative), 0) FROM steam_app_reviews", nativeQuery = true)
    long sumTotalReviews();

    @Query(value = "SELECT COALESCE(MIN(updated_at), now()) FROM steam_app_reviews", nativeQuery = true)
    OffsetDateTime minUpdatedAt();

    @Query(value = "SELECT COALESCE(MAX(updated_at), now()) FROM steam_app_reviews", nativeQuery = true)
    OffsetDateTime maxUpdatedAt();

    /**
     * Randomly selects eligible apps within a review-count range.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) BETWEEN :lower AND :upper
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomBetween(@Param("sinceDate") LocalDate sinceDate,
                                 @Param("lower") int lower,
                                 @Param("upper") int upper,
                                 Pageable pageable);

    /**
     * Randomly selects eligible apps at or above a lower bound.
     * Uses NOT EXISTS anti-joins and limits ORDER BY random() to the eligible subset.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT r.app_id
                FROM steam_app_reviews r
                WHERE (r.total_positive + r.total_negative) >= :lower
                  AND NOT EXISTS (
                      SELECT 1
                      FROM review_game_pick p
                      WHERE p.app_id = r.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = r.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
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


