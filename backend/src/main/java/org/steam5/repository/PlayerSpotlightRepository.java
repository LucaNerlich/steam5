package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.PlayerSpotlight;

import java.time.LocalDate;
import java.util.List;

public interface PlayerSpotlightRepository extends JpaRepository<PlayerSpotlight, LocalDate> {

    /** Most recent spotlights for a player, newest first, capped for a "condensed" profile list. */
    List<PlayerSpotlight> findTop10BySteamIdOrderByGameDateDesc(String steamId);
}
