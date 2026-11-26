package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.domain.details.SteamAppDetailService;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReviewGameStateControllerArchiveTest {

    private static ReviewGameStateController controller() {
        return new ReviewGameStateController(
                mock(ReviewGameStateService.class),
                mock(SteamAppDetailService.class),
                mock(GuessRepository.class),
                mock(SteamAppReviewsRepository.class),
                mock(ReviewGamePickRepository.class),
                mock(Scheduler.class),
                mock(MeterRegistry.class));
    }

    @Test
    void archiveMonth_rejectsAMonthWhoseUpperBoundOverflowsTheYearRange() {
        final var response = controller().archiveMonth("+999999999-12");
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void isCurrentMonth_readsTheUtcDayNotTheHostClock() {
        // The archive month's cache header used YearMonth.now() (JVM default zone) while every
        // other consumer of the game calendar is UTC, so a host behind UTC marked the new month
        // historical and served it immutable for a year.
        assertTrue(ReviewGameStateController.isCurrentMonth(
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 1)));
        assertFalse(ReviewGameStateController.isCurrentMonth(
                YearMonth.of(2026, 10), LocalDate.of(2026, 11, 1)));
        assertFalse(ReviewGameStateController.isCurrentMonth(
                YearMonth.of(2026, 12), LocalDate.of(2026, 11, 30)));
    }

    @Test
    void archiveMonth_rejectsAnUnparseableMonth() {
        final var response = controller().archiveMonth("not-a-month");
        assertEquals(400, response.getStatusCode().value());
    }
}
