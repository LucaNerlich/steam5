package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #703 follow-up: /my/history must count only guesses whose pick is still served for that
 * day — the archive index sums this list against a baseline of current picks.
 */
class ReviewGameStateControllerHistoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void myHistory_dropsGuessesForPicksNoLongerServed() {
        final ReviewGameStateService service = mock(ReviewGameStateService.class);
        final GuessRepository guessRepository = mock(GuessRepository.class);
        final ReviewGamePickRepository pickRepository = mock(ReviewGamePickRepository.class);
        final ReviewGameStateController controller = new ReviewGameStateController(
                service,
                mock(SteamAppDetailService.class),
                guessRepository,
                mock(SteamAppReviewsRepository.class),
                pickRepository,
                mock(Scheduler.class),
                mock(MeterRegistry.class));

        final LocalDate day = GameDate.todayUtc().minusDays(1);
        when(pickRepository.findByPickDateBetween(any(), any())).thenReturn(List.of(
                new ReviewGamePick(1L, day, 42L, OffsetDateTime.now())));
        when(guessRepository.findBySteamIdBetween("u1", LocalDate.of(1970, 1, 1), GameDate.todayUtc()))
                .thenReturn(List.of(
                        new Guess(1L, "u1", day, 1, 42L, "1-100", "1-100", 5, OffsetDateTime.now()),
                        new Guess(2L, "u1", day, 1, 99L, "101-1000", "1-100", 0, OffsetDateTime.now())));

        final var res = controller.myHistory(null, null, "u1");

        assertEquals(200, res.getStatusCode().value());
        final List<ReviewGameStateController.MyGuessDto> body =
                (List<ReviewGameStateController.MyGuessDto>) res.getBody();
        assertNotNull(body);
        assertEquals(1, body.size(), "only the still-served pick's guess may count");
        assertEquals(42L, body.getFirst().appId());
    }
}
