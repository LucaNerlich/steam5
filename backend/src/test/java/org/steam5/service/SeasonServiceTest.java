package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.steam5.config.SeasonProperties;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonStatus;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.SeasonAwardResultRepository;
import org.steam5.repository.SeasonRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeasonServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonAwardResultRepository awardResultRepository;
    private GuessRepository guessRepository;
    private SeasonCreatorService seasonCreator;
    private SeasonProperties seasonProperties;
    private SeasonService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        awardResultRepository = mock(SeasonAwardResultRepository.class);
        guessRepository = mock(GuessRepository.class);
        seasonCreator = mock(SeasonCreatorService.class);
        seasonProperties = new SeasonProperties();
        service = new SeasonService(seasonRepository, awardResultRepository, guessRepository,
                seasonProperties, seasonCreator);
    }

    private static Season seasonWith(int number, LocalDate start, LocalDate end, SeasonStatus status) {
        Season s = new Season();
        s.setId((long) number);
        s.setSeasonNumber(number);
        s.setStartDate(start);
        s.setEndDate(end);
        s.setStatus(status);
        return s;
    }

    // ensureSeasonForDate ---------------------------------------------------------------

    @Test
    void ensureSeasonForDate_returnsExistingSeasonWhenFound() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season existing = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.of(existing));

        Season result = service.ensureSeasonForDate(date);

        assertSame(existing, result);
        verify(seasonCreator, never()).createSeasonsUntil(any());
    }

    @Test
    void ensureSeasonForDate_callsCreatorWhenNoSeasonFound() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season created = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty());
        when(seasonCreator.createSeasonsUntil(date)).thenReturn(created);

        Season result = service.ensureSeasonForDate(date);

        assertSame(created, result);
        verify(seasonCreator, times(1)).createSeasonsUntil(date);
    }

    @Test
    void ensureSeasonForDate_retriesOnDataIntegrityViolation() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season recovered = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recovered));
        when(seasonCreator.createSeasonsUntil(date))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        Season result = service.ensureSeasonForDate(date);

        assertSame(recovered, result);
        verify(seasonRepository, times(2))
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    @Test
    void ensureSeasonForDate_throwsWhenSecondFindAlsoFails() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty());
        when(seasonCreator.createSeasonsUntil(date))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        assertThrows(IllegalStateException.class, () -> service.ensureSeasonForDate(date));
    }

    @Test
    void ensureSeasonForDate_throwsOnNullDate() {
        assertThrows(NullPointerException.class, () -> service.ensureSeasonForDate(null));
    }

    // findSeasonContaining --------------------------------------------------------------

    @Test
    void findSeasonContaining_delegatesToRepository() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        Season s = seasonWith(2, date.minusDays(3), date.plusDays(3), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.of(s));

        Optional<Season> result = service.findSeasonContaining(date);

        assertTrue(result.isPresent());
        assertSame(s, result.get());
        verify(seasonRepository, times(1))
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    // listSeasonsDescending -------------------------------------------------------------

    @Test
    void listSeasonsDescending_returnsRepositoryResult() {
        List<Season> seasons = List.of(
                seasonWith(2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), SeasonStatus.ACTIVE),
                seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), SeasonStatus.FINALIZED)
        );
        when(seasonRepository.findAllByOrderBySeasonNumberDesc()).thenReturn(seasons);

        List<Season> result = service.listSeasonsDescending();

        assertEquals(seasons, result);
        verify(seasonRepository, times(1)).findAllByOrderBySeasonNumberDesc();
    }

    // buildSeasonReport -----------------------------------------------------------------

    @Test
    void buildSeasonReport_returnsEmptyStatsForFutureSeasonWithNoData() {
        LocalDate today = LocalDate.now();
        Season future = seasonWith(99, today.plusDays(10), today.plusDays(40), SeasonStatus.ACTIVE);

        SeasonService.SeasonReport report = service.buildSeasonReport(future);

        assertNotNull(report);
        assertTrue(report.players().isEmpty());
        assertNotNull(report.summary());
        assertEquals(0, report.summary().totalPlayers());
        assertNull(report.summary().dataThrough());
        verify(guessRepository, never()).findSeasonStats(any(), any());
    }

    @Test
    void buildSeasonReport_throwsOnNullSeason() {
        assertThrows(NullPointerException.class, () -> service.buildSeasonReport(null));
    }

    // backfillHistoricalSeasons ---------------------------------------------------------

    @Test
    void backfillHistoricalSeasons_returnsEmptyListWhenNoGuesses() {
        when(guessRepository.findEarliestGameDate()).thenReturn(Optional.empty());

        List<Season> result = service.backfillHistoricalSeasons();

        assertTrue(result.isEmpty());
        verify(seasonRepository, never()).save(any());
    }
}
