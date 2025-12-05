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

    @Query(value = """
            SELECT DISTINCT d.app_id
            FROM steam_app_details d
            LEFT JOIN steam_app_genre sag ON d.app_id = sag.app_id
            LEFT JOIN genre g ON sag.genre_id = g.id
            LEFT JOIN steam_app_category sac ON d.app_id = sac.app_id
            LEFT JOIN category c ON sac.category_id = c.id
            WHERE (:genre IS NULL OR lower(g.description) = lower(:genre))
              AND (:category IS NULL OR lower(c.description) = lower(:category))
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> pickRandomAppIds(@Param("genre") String genre,
                                @Param("category") String category,
                                @Param("limit") int limit);
}


