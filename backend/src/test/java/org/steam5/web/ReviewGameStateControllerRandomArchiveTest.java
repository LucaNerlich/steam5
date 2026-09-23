package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.steam5.domain.GameDate;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers GET /api/review-game/archive/random: a historical-day pick used to keep playing after round 5. */
public class ReviewGameStateControllerRandomArchiveTest {

    private ReviewGamePickRepository pickRepository;
    private ReviewGameStateController controller;

    @BeforeEach
    void setUp() {
        final ReviewGameStateService service = mock(ReviewGameStateService.class);
        final SteamAppDetailRepository detailRepository = mock(SteamAppDetailRepository.class);
        final GuessRepository guessRepository = mock(GuessRepository.class);
        final SteamAppReviewsRepository reviewsRepository = mock(SteamAppReviewsRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        pickRepository = mock(ReviewGamePickRepository.class);
        final Scheduler scheduler = mock(Scheduler.class);
        final MeterRegistry meterRegistry = mock(MeterRegistry.class);
        controller = new ReviewGameStateController(service, detailRepository, guessRepository,
                reviewsRepository, userRepository, pickRepository, scheduler, meterRegistry,
                mock(PlatformTransactionManager.class));
    }

    @Test
    void randomArchiveDate_returnsDateWithNoStoreCacheControl() {
        final LocalDate historical = GameDate.todayUtc().minusDays(3);
        when(pickRepository.findRandomArchiveDate(GameDate.todayUtc())).thenReturn(Optional.of(historical));

        final ResponseEntity<Map<String, String>> res = controller.randomArchiveDate();

        assertEquals(200, res.getStatusCode().value());
        assertEquals(historical.toString(), res.getBody().get("date"));
        assertEquals("no-store", res.getHeaders().getCacheControl());
    }

    @Test
    void randomArchiveDate_returnsNotFoundWhenNoHistoricalDayExists() {
        when(pickRepository.findRandomArchiveDate(GameDate.todayUtc())).thenReturn(Optional.empty());

        final ResponseEntity<Map<String, String>> res = controller.randomArchiveDate();

        assertEquals(404, res.getStatusCode().value());
        assertNull(res.getBody());
    }

    @Test
    void alwaysPickHistory_malformedDatesReturn400() {
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of());
        assertEquals(400, controller.alwaysPickHistory("2024-13-99", null).getStatusCode().value());
        assertEquals(400, controller.alwaysPickHistory("2024-01-01", "garbage").getStatusCode().value());
        assertEquals(400, controller.alwaysPickHistory("2024-12-31", "2024-01-01").getStatusCode().value());
    }

    @Test
    void alwaysPickHistory_validRangeReturns200() {
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of());
        final var res = controller.alwaysPickHistory("2024-01-01", "2024-01-31");
        assertEquals(200, res.getStatusCode().value());
    }
}
