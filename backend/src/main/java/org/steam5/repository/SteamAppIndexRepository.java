package org.steam5.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.SteamAppIndex;

@Repository
public interface SteamAppIndexRepository extends JpaRepository<SteamAppIndex, Long> {
    Page<SteamAppIndex> findByAppIdGreaterThan(Long appId, Pageable pageable);
}


