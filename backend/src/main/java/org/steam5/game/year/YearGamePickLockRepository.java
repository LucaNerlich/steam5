package org.steam5.game.year;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface YearGamePickLockRepository extends JpaRepository<YearGamePickLock, LocalDate> {

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO year_game_pick_lock(pick_date, created_at) VALUES (:date, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int tryAcquire(@Param("date") LocalDate date);
}
