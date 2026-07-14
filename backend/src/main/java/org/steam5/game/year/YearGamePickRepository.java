package org.steam5.game.year;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YearGamePickRepository extends JpaRepository<YearGamePick, Long> {

    List<YearGamePick> findByPickDate(LocalDate pickDate);

    List<YearGamePick> findByPickDateBetween(LocalDate start, LocalDate end);

    @Query("select distinct p.pickDate from YearGamePick p order by p.pickDate desc")
    List<LocalDate> listDistinctPickDates(Pageable pageable);

    @Query(value = """
            WITH eligible AS (
                SELECT DISTINCT pick_date FROM year_game_pick WHERE pick_date < :today
            )
            SELECT pick_date FROM eligible ORDER BY random() LIMIT 1
            """, nativeQuery = true)
    Optional<LocalDate> findRandomArchiveDate(@Param("today") LocalDate today);

    interface MonthlyArchivePickRow {
        LocalDate getPickDate();

        Long getAppId();

        String getName();
    }

    @Query(value = """
            SELECT p.pick_date AS pickDate,
                   p.app_id AS appId,
                   COALESCE(d.name, CAST(p.app_id AS text)) AS name
            FROM year_game_pick p
            LEFT JOIN steam_app_details d ON d.app_id = p.app_id
            WHERE p.pick_date >= :fromDate
              AND p.pick_date < :toDate
            ORDER BY p.pick_date DESC, p.created_at ASC, p.id ASC
            """, nativeQuery = true)
    List<MonthlyArchivePickRow> listMonthlyArchivePicks(@Param("fromDate") LocalDate fromDate,
                                                        @Param("toDate") LocalDate toDate);
}
