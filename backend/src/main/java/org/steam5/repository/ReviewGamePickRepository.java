package org.steam5.repository;

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
}


