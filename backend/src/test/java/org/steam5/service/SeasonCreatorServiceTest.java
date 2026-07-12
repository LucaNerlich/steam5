package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.config.SeasonProperties;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonStatus;
import org.steam5.repository.SeasonRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeasonCreatorServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonProperties seasonProperties;
    private SeasonCreatorService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        seasonProperties = new SeasonProperties();
        seasonProperties.setLengthDays(7);
        service = new SeasonCreatorService(seasonRepository, seasonProperties);

        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSeasonsUntil_createsFirstSeasonWhenNoneExist() {
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.empty());
        final LocalDate date = LocalDate.of(2026, 1, 1);

        final Season result = service.createSeasonsUntil(date);

        final ArgumentCaptor<Season> captor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository, times(1)).save(captor.capture());
        final Season saved = captor.getValue();
        assertEquals(1, saved.getSeasonNumber());
        assertEquals(date, saved.getStartDate());
        assertEquals(date.plusDays(6), saved.getEndDate());
        assertEquals(SeasonStatus.ACTIVE, saved.getStatus());
        assertSame(saved, result);
    }

    @Test
    void createSeasonsUntil_returnsExistingSeasonWhenDateIsWithinIt() {
        // Use a 30-day season for this scenario so 2026-01-15 falls within it.
        seasonProperties.setLengthDays(30);
        final Season existing = new Season();
        existing.setSeasonNumber(1);
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(LocalDate.of(2026, 1, 30));
        existing.setStatus(SeasonStatus.ACTIVE);
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.of(existing));

        final Season result = service.createSeasonsUntil(LocalDate.of(2026, 1, 15));

        assertSame(existing, result);
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void createSeasonsUntil_createsOneGapSeason() {
        final Season last = new Season();
        last.setSeasonNumber(1);
        last.setStartDate(LocalDate.of(2026, 1, 1));
        last.setEndDate(LocalDate.of(2026, 1, 30));
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.of(last));

        // Length is 7 days, so next season 2026-01-31..2026-02-06 covers 2026-02-06? No, need to reach 2026-02-15.
        // Let's use a date within a single 7-day gap season: 2026-02-05 is within 2026-01-31..2026-02-06.
        final Season result = service.createSeasonsUntil(LocalDate.of(2026, 2, 5));

        final ArgumentCaptor<Season> captor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository, times(1)).save(captor.capture());
        final Season saved = captor.getValue();
        assertEquals(2, saved.getSeasonNumber());
        assertEquals(LocalDate.of(2026, 1, 31), saved.getStartDate());
        assertEquals(LocalDate.of(2026, 2, 6), saved.getEndDate());
        assertSame(saved, result);
    }

    @Test
    void createSeasonsUntil_chainsMultipleGapSeasons() {
        final Season last = new Season();
        last.setSeasonNumber(1);
        last.setStartDate(LocalDate.of(2026, 1, 1));
        last.setEndDate(LocalDate.of(2026, 1, 30));
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.of(last));

        final LocalDate target = LocalDate.of(2026, 3, 1);
        final Season result = service.createSeasonsUntil(target);

        verify(seasonRepository, atLeast(2)).save(any(Season.class));
        assertNotNull(result.getEndDate());
        assertFalse(result.getEndDate().isBefore(target),
                "Returned season endDate " + result.getEndDate() + " must be >= " + target);
    }
}
