package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.DailyPickLock;

import java.time.LocalDate;

public interface DailyPickLockRepository extends JpaRepository<DailyPickLock, LocalDate> {

    /**
     * Non-blocking per-day generation lock. The previous {@code INSERT ... ON
     * CONFLICT DO NOTHING} blocked on the unique index for the whole duration of
     * the winner's transaction (which used to span slow Steam enrichment); with
     * the production {@code statement_timeout} that turned concurrent day-rollover
     * requests into 500s. {@code pg_try_advisory_xact_lock} returns immediately
     * and is released automatically when the transaction ends.
     *
     * <p>Two-key form namespaces this lock class ({@code daily-pick-lock}) so a
     * 32-bit {@code hashtext(date)} collision cannot block an unrelated advisory
     * lock, and two dates hashing to the same int still contend only within
     * this namespace.</p>
     */
    @Transactional
    @Query(value = "SELECT pg_try_advisory_xact_lock(hashtext('daily-pick-lock'), hashtext(:date))", nativeQuery = true)
    boolean tryAcquire(@Param("date") String date);
}
