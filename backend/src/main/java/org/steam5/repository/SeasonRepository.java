package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findBySeasonNumber(int seasonNumber);

    Optional<Season> findTopByOrderBySeasonNumberDesc();

    Optional<Season> findFirstByOrderBySeasonNumberAsc();

    Optional<Season> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate startInclusive, LocalDate endInclusive);

    List<Season> findAllByStatusOrderBySeasonNumberAsc(SeasonStatus status);

    List<Season> findAllByOrderBySeasonNumberDesc();

    /**
     * Atomically claims a season for finalization: only an ACTIVE season can be
     * claimed, and only one concurrent finalizer wins (exactly 1 row updated).
     * The status flip happens before any award computation, so a second instance
     * (or a re-finalization attempt) sees zero rows and must skip.
     */
    @Modifying
    @Query("UPDATE Season s SET s.status = org.steam5.domain.SeasonStatus.FINALIZED WHERE s.id = :id AND s.status = org.steam5.domain.SeasonStatus.ACTIVE")
    int claimForFinalization(@Param("id") Long id);

    /**
     * Shifts all season numbers up by {@code shift} in one statement. Callers
     * must ensure no collision with the unique season_number index (used by the
     * historical backfill to make room for seasons before season #1).
     * {@code clearAutomatically} is required: the persistence context still
     * holds the pre-shift season numbers, and any subsequent read would
     * otherwise return stale entities (e.g. a forward fill inserting a
     * colliding season number).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE seasons SET season_number = season_number + :shift", nativeQuery = true)
    int shiftSeasonNumbersUpBy(@Param("shift") int shift);
}
