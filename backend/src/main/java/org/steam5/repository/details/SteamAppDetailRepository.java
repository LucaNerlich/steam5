package org.steam5.repository.details;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.SteamAppDetail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SteamAppDetailRepository extends JpaRepository<SteamAppDetail, Long> {
    long countByIsFreeTrue();

    long countByIsFreeFalse();

    long countByIsWindowsTrue();

    long countByIsMacTrue();

    long countByIsLinuxTrue();

    @Query(value = "SELECT g.description AS label, COUNT(*) AS count " +
            "FROM steam_app_genre sag " +
            "JOIN genre g ON sag.genre_id = g.id " +
            "GROUP BY g.description " +
            "ORDER BY count DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<LabelCountProjection> topGenres(@Param("limit") int limit);

    @Query(value = "SELECT c.description AS label, COUNT(*) AS count " +
            "FROM steam_app_category sac " +
            "JOIN category c ON sac.category_id = c.id " +
            "GROUP BY c.description " +
            "ORDER BY count DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<LabelCountProjection> topCategories(@Param("limit") int limit);

    interface LabelCountProjection {
        String getLabel();

        long getCount();
    }

    @EntityGraph(attributePaths = {
            "priceOverview",
            "developers",
            "publisher",
            "genres",
            "categories",
            "screenshots",
            "movies"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    Optional<SteamAppDetail> findByAppId(Long appId);

    @EntityGraph(attributePaths = {
            "priceOverview",
            "developers",
            "publisher",
            "genres",
            "categories",
            "screenshots",
            "movies"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    @Query("select distinct d from SteamAppDetail d where d.appId in :ids")
    List<SteamAppDetail> findAllByAppIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Randomly selects eligible apps whose parsed release year falls within a closed range.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT d.app_id
                FROM steam_app_details d
                WHERE d.release_date IS NOT NULL
                  AND d.release_date <> ''
                  AND d.release_date !~* 'coming soon'
                  AND d.release_date !~* 'to be announced'
                  AND substring(d.release_date from '(\\d{4})') IS NOT NULL
                  AND CAST(substring(d.release_date from '(\\d{4})') AS INTEGER) BETWEEN :lower AND :upper
                  AND NOT EXISTS (
                      SELECT 1
                      FROM year_game_pick p
                      WHERE p.app_id = d.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = d.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomByReleaseYearBetween(@Param("sinceDate") java.time.LocalDate sinceDate,
                                              @Param("lower") int lower,
                                              @Param("upper") int upper,
                                              org.springframework.data.domain.Pageable pageable);

    /**
     * Randomly selects eligible apps whose parsed release year is at or above a lower bound.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT d.app_id
                FROM steam_app_details d
                WHERE d.release_date IS NOT NULL
                  AND d.release_date <> ''
                  AND d.release_date !~* 'coming soon'
                  AND d.release_date !~* 'to be announced'
                  AND substring(d.release_date from '(\\d{4})') IS NOT NULL
                  AND CAST(substring(d.release_date from '(\\d{4})') AS INTEGER) >= :lower
                  AND NOT EXISTS (
                      SELECT 1
                      FROM year_game_pick p
                      WHERE p.app_id = d.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = d.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomByReleaseYearGte(@Param("sinceDate") java.time.LocalDate sinceDate,
                                          @Param("lower") int lower,
                                          org.springframework.data.domain.Pageable pageable);

    /**
     * Fallback random selection for any app with a parseable release year.
     */
    @Query(value = """
            WITH eligible AS (
                SELECT d.app_id
                FROM steam_app_details d
                WHERE d.release_date IS NOT NULL
                  AND d.release_date <> ''
                  AND d.release_date !~* 'coming soon'
                  AND d.release_date !~* 'to be announced'
                  AND substring(d.release_date from '(\\d{4})') IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM year_game_pick p
                      WHERE p.app_id = d.app_id
                        AND p.pick_date >= :sinceDate
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM excluded_app x
                      WHERE x.app_id = d.app_id
                  )
            )
            SELECT app_id
            FROM eligible
            ORDER BY random()
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<Long> findRandomAnyReleaseYear(@Param("sinceDate") java.time.LocalDate sinceDate,
                                        org.springframework.data.domain.Pageable pageable);
}


