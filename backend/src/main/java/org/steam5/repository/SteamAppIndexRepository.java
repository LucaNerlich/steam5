package org.steam5.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.steam5.domain.SteamAppIndex;

@Repository
public interface SteamAppIndexRepository extends JpaRepository<SteamAppIndex, Long> {
    Page<SteamAppIndex> findByAppIdGreaterThan(Long appId, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM steam_app_index i JOIN steam_app_reviews r ON r.app_id = i.app_id", nativeQuery = true)
    long countWithReviews();

    @Query(value = "SELECT COUNT(*) FROM steam_app_index i JOIN steam_app_details d ON d.app_id = i.app_id", nativeQuery = true)
    long countWithDetails();
}


