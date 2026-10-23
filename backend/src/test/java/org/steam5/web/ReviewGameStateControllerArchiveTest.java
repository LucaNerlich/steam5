package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.domain.details.SteamAppDetailService;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void archiveMonth_rejectsAnUnparseableMonth() {
        final var response = controller().archiveMonth("not-a-month");
        assertEquals(400, response.getStatusCode().value());
    }
}
