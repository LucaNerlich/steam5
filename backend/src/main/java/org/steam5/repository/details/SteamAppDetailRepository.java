package org.steam5.repository.details;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.SteamAppDetail;

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
    java.util.List<LabelCountProjection> topGenres(@Param("limit") int limit);

    @Query(value = "SELECT c.description AS label, COUNT(*) AS count " +
            "FROM steam_app_category sac " +
            "JOIN category c ON sac.category_id = c.id " +
            "GROUP BY c.description " +
            "ORDER BY count DESC " +
            "LIMIT :limit", nativeQuery = true)
    java.util.List<LabelCountProjection> topCategories(@Param("limit") int limit);

    interface LabelCountProjection {
        String getLabel();

        long getCount();
    }

    @EntityGraph(attributePaths = {
            // Only to-one association to avoid multiple bag fetch when joining lists
            "priceOverview"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    java.util.Optional<SteamAppDetail> findByAppId(Long appId);

    @EntityGraph(attributePaths = {
            // Keep list fetch light to avoid multiple bag fetch; include only to-one association
            "priceOverview"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    @Query("select d from SteamAppDetail d where d.appId in :ids")
    java.util.List<SteamAppDetail> findAllByAppIdIn(@Param("ids") java.util.Collection<Long> ids);
}


