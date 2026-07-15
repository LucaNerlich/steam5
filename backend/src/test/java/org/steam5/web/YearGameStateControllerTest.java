package org.steam5.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.domain.YearGuess;
import org.steam5.game.year.YearGamePick;
import org.steam5.game.year.YearGamePickRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.YearGuessRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.YearGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
                new YearGameStateController.GuessRequest(42L, 2010, null));
        assertTrue(wrong.getStatusCode().is2xxSuccessful());
        assertNotNull(wrong.getBody());
        assertFalse(wrong.getBody().correct());
        assertNull(wrong.getBody().releaseYear());
        assertNull(wrong.getBody().distance());
        assertTrue(wrong.getBody().guessTooEarly());

        final ResponseEntity<YearGameStateController.GuessResponse> correct = controller.submitGuess(
                new YearGameStateController.GuessRequest(42L, 2020, null));
        assertTrue(correct.getBody().correct());
        assertEquals(2020, correct.getBody().releaseYear());
        assertEquals(5, correct.getBody().points());
    }

    @Test
    void submitGuessAuthenticated_scoresWithHintsUsedFromPersistedProgress() {
        final LocalDate today = LocalDate.of(2026, 7, 15);
        final YearGamePick pick = new YearGamePick(1L, today, 42L, OffsetDateTime.now());
        final org.steam5.game.year.YearGameConfig config = new org.steam5.game.year.YearGameConfig();

        when(service.generateDailyPicks()).thenReturn(List.of(pick));
        when(service.getReleaseYearForApp(42L)).thenReturn(2020);
        when(service.getConfig()).thenReturn(config);
        when(userRepository.existsById("steam-1")).thenReturn(true);

        final YearGuess inProgress = new YearGuess(
                9L,
                "steam-1",
                today,
                1,
                42L,
                2010,
                2020,
                1,
                10,
                false,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("steam-1", today, 1))
                .thenReturn(Optional.of(inProgress))
                .thenReturn(Optional.of(inProgress));

        when(guessRepository.save(any(YearGuess.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final ResponseEntity<YearGameStateController.GuessResponse> response = controller.submitGuessAuthenticated(
                "steam-1",
                new YearGameStateController.GuessRequest(42L, 2020, null));

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().correct());
        assertEquals(1, response.getBody().hintsUsed());
        assertEquals(4, response.getBody().points());
        verify(guessRepository).flush();
        verify(guessRepository, atLeastOnce()).save(argThat(saved ->
                saved.isCompleted() && saved.getHintsUsed() == 1 && saved.getPoints() == 4));
    }

    @Test
    void submitGuessAuthenticated_usesClientHintsUsedWhenStoredProgressLags() {
        final LocalDate today = LocalDate.of(2026, 7, 15);
        final YearGamePick pick = new YearGamePick(1L, today, 42L, OffsetDateTime.now());
        final org.steam5.game.year.YearGameConfig config = new org.steam5.game.year.YearGameConfig();

        when(service.generateDailyPicks()).thenReturn(List.of(pick));
        when(service.getReleaseYearForApp(42L)).thenReturn(2020);
        when(service.getConfig()).thenReturn(config);
        when(userRepository.existsById("steam-1")).thenReturn(true);

        final YearGuess inProgress = new YearGuess(
                9L,
                "steam-1",
                today,
                1,
                42L,
                2010,
                2020,
                0,
                10,
                false,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("steam-1", today, 1))
                .thenReturn(Optional.of(inProgress))
                .thenReturn(Optional.of(inProgress));

        when(guessRepository.save(any(YearGuess.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final ResponseEntity<YearGameStateController.GuessResponse> response = controller.submitGuessAuthenticated(
                "steam-1",
                new YearGameStateController.GuessRequest(42L, 2020, 3));

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().hintsUsed());
        assertEquals(2, response.getBody().points());
        verify(guessRepository, atLeastOnce()).save(argThat(saved ->
                saved.isCompleted() && saved.getHintsUsed() == 3 && saved.getPoints() == 2));
    }
}
