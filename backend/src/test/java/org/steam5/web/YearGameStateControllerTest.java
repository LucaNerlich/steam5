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
    void getToday_hidesReleaseDateAndIncludesHintTiers() {
        final LocalDate today = LocalDate.of(2026, 3, 1);
        final YearGamePick pick = new YearGamePick(1L, today, 42L, OffsetDateTime.now());
        final SteamAppDetail detail = new SteamAppDetail();
        detail.setAppId(42L);
        detail.setName("Test Game");
        detail.setReleaseDate("19 Nov, 2020");

        when(service.generateDailyPicks()).thenReturn(List.of(pick));
        when(service.getHintTiers()).thenReturn(List.of(
                new YearGameStateService.HintTierMeta(0, "No hints", "Guess freely.", 5)
        ));
        when(detailRepository.findAllByAppIdIn(anyList())).thenReturn(List.of(detail));
        when(service.sanitizeForGameplay(org.mockito.ArgumentMatchers.any(SteamAppDetail.class)))
                .thenAnswer(invocation -> {
                    final SteamAppDetail d = invocation.getArgument(0);
                    d.setReleaseDate(null);
                    return d;
                });

        final ResponseEntity<YearGameStateController.YearGameStateDto> response =
                controller.getToday(new HttpHeaders());

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(today, response.getBody().date());
        assertEquals(1, response.getBody().hintTiers().size());
        assertNull(response.getBody().picks().getFirst().getReleaseDate());
    }

    @Test
    void submitGuess_exactYearDoesNotRevealAnswerWhenWrong() {
        when(service.getReleaseYearForApp(42L)).thenReturn(2020);
        when(service.getConfig()).thenReturn(new org.steam5.game.year.YearGameConfig());

        final ResponseEntity<YearGameStateController.GuessResponse> wrong = controller.submitGuess(
                new YearGameStateController.GuessRequest(42L, 2010));
        assertTrue(wrong.getStatusCode().is2xxSuccessful());
        assertNotNull(wrong.getBody());
        assertFalse(wrong.getBody().correct());
        assertNull(wrong.getBody().releaseYear());
        assertEquals(10, wrong.getBody().distance());

        final ResponseEntity<YearGameStateController.GuessResponse> correct = controller.submitGuess(
                new YearGameStateController.GuessRequest(42L, 2020));
        assertTrue(correct.getBody().correct());
        assertEquals(2020, correct.getBody().releaseYear());
        assertEquals(5, correct.getBody().points());
    }
}
