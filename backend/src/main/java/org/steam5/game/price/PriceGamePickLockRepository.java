package org.steam5.game.price;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface PriceGamePickLockRepository extends JpaRepository<PriceGamePickLock, LocalDate> {

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO price_game_pick_lock(pick_date, created_at) VALUES (:date, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int tryAcquire(@Param("date") LocalDate date);
}
