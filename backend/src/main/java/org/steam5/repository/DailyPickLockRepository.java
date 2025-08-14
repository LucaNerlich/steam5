package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.DailyPickLock;

import java.time.LocalDate;

public interface DailyPickLockRepository extends JpaRepository<DailyPickLock, LocalDate> {

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO daily_pick_lock(pick_date, created_at) VALUES (:date, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int tryAcquire(@Param("date") LocalDate date);
}


