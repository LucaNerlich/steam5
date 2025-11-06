package org.steam5.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.ReviewGamePick;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReviewGamePickRepository extends JpaRepository<ReviewGamePick, Long> {

    List<ReviewGamePick> findByPickDate(LocalDate pickDate);

    @Query(value = "SELECT app_id FROM review_game_pick WHERE pick_date >= :sinceDate", nativeQuery = true)
    List<Long> findAppIdsPickedSince(@Param("sinceDate") LocalDate sinceDate);

    @Query(value = "SELECT COUNT(DISTINCT pick_date) FROM review_game_pick", nativeQuery = true)
    long countDistinctPickDates();

    @Query(value = "SELECT COALESCE(MAX(pick_date), DATE '1970-01-01') FROM review_game_pick", nativeQuery = true)
    LocalDate findLatestPickDate();

    @Query("select distinct p.pickDate from ReviewGamePick p order by p.pickDate desc")
    List<LocalDate> listDistinctPickDates(Pageable pageable);

    // Top games by review count
    interface TopGameByReviewsRow {
        Long getAppId();
        String getName();
        Long getTotalReviews();
    }

    @Query(value = "SELECT p.app_id AS appId, MAX(d.name) AS name, " +
            "MAX(r.total_positive + r.total_negative) AS totalReviews " +
            "FROM review_game_pick p " +
            "JOIN steam_app_reviews r ON r.app_id = p.app_id " +
            "LEFT JOIN steam_app_details d ON d.app_id = p.app_id " +
            "WHERE (r.total_positive + r.total_negative) > 0 " +
            "GROUP BY p.app_id " +
            "ORDER BY MAX(r.total_positive + r.total_negative) DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TopGameByReviewsRow> findTopGamesByReviewCount(@Param("limit") int limit);
}


