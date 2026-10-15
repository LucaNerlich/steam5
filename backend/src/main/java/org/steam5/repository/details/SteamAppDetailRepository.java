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

    // Only `priceOverview` (a to-one) rides in the entity graph: a join fetch of
    // the six collections would transfer their cartesian product for every read
    // (Hibernate then discards the duplicates during distinct processing). The
    // collections are initialized in-transaction by
    // SteamAppDetailService#findByAppId / #findAllByAppIdIn, which lets
    // Hibernate load each one with its own batched query
    // (hibernate.default_batch_fetch_size).
    @EntityGraph(attributePaths = {
            "priceOverview"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    Optional<SteamAppDetail> findByAppId(Long appId);

    @EntityGraph(attributePaths = {
            "priceOverview"
    }, type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    @Query("select distinct d from SteamAppDetail d where d.appId in :ids")
    List<SteamAppDetail> findAllByAppIdIn(@Param("ids") Collection<Long> ids);
}


