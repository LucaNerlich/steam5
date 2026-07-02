package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.PlayerSpotlight;

import java.time.LocalDate;

public interface PlayerSpotlightRepository extends JpaRepository<PlayerSpotlight, LocalDate> {
}
