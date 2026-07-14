package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.game.year.YearGamePick;
import org.steam5.game.year.YearGamePickRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.YearGuessRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.YearGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YearGameStateControllerTest {

    private YearGameStateService service;
    private SteamAppDetailRepository detailRepository;
    private YearGuessRepository guessRepository;
    private UserRepository userRepository;
    private YearGamePickRepository pickRepository;
    private Scheduler scheduler;
    private MeterRegistry meterRegistry;

    private YearGameStateController controller;

    @BeforeEach
    void setUp() {
        service = mock(YearGameStateService.class);
        detailRepository = mock(SteamAppDetailRepository.class);
        guessRepository = mock(YearGuessRepository.class);
        userRepository = mock(UserRepository.class);
        pickRepository = mock(YearGamePickRepository.class);
        scheduler = mock(Scheduler.class);
        meterRegistry = mock(MeterRegistry.class);
        controller = new YearGameStateController(service, detailRepository, guessRepository,
                userRepository, pickRepository, scheduler, meterRegistry);
    }

    @Test
    void getToday_returnsBucketsAndPicks() {
        final LocalDate today = LocalDate.of(2026, 3, 1);
        final YearGamePick pick = new YearGamePick(1L, today, 42L, OffsetDateTime.now());
        final SteamAppDetail detail = new SteamAppDetail();
        detail.setAppId(42L);
        detail.setName("Test Game");
        detail.setReleaseDate("19 Nov, 2020");

        when(service.generateDailyPicks()).thenReturn(List.of(pick));
        when(service.getBucketLabels()).thenReturn(List.of("1-1999", "2000-2009", "2010-2019", "2019+"));
        when(service.getBucketTitles()).thenReturn(List.of("", "", "", ""));
        when(detailRepository.findAllByAppIdIn(anyList())).thenReturn(List.of(detail));

        final ResponseEntity<YearGameStateController.YearGameStateDto> response =
                controller.getToday(new HttpHeaders());

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(today, response.getBody().date());
        assertEquals(4, response.getBody().buckets().size());
        assertEquals(1, response.getBody().picks().size());
        assertEquals("Test Game", response.getBody().picks().getFirst().getName());
    }

    @Test
    void submitGuess_returnsReleaseYearBucket() {
        when(service.getReleaseYearForApp(42L)).thenReturn(2020);
        when(service.inferBucket(2020)).thenReturn("2019+");

        final ResponseEntity<YearGameStateController.GuessResponse> response = controller.submitGuess(
                new YearGameStateController.GuessRequest(42L, "2019+"));

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(2020, response.getBody().releaseYear());
        assertEquals("2019+", response.getBody().actualBucket());
        assertTrue(response.getBody().correct());
    }
}
