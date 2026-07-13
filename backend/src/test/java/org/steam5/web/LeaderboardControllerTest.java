package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.GuessRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LeaderboardControllerTest {

    private GuessRepository guessRepository;
    private ReviewGameStateService reviewGameStateService;
    private SeasonService seasonService;
    private CacheManager cacheManager;
    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        seasonService = mock(SeasonService.class);
        cacheManager = mock(CacheManager.class);
        leaderboardService = mock(LeaderboardService.class);
    }

    @Test
    void today_fetchesGuessesThenDelegatesToService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate pickDate = LocalDate.now();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of(new ReviewGamePick(1L, pickDate, 42L, OffsetDateTime.now())));

        Guess g1 = new Guess(1L, "u1", pickDate, 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        List<Guess> guesses = List.of(g1);
        when(guessRepository.findAllByDate(pickDate)).thenReturn(guesses);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildLeaderboard(guesses, pickDate)).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.today();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertSame(canned, res.getBody());
        verify(guessRepository).findAllByDate(pickDate);
        verify(leaderboardService).buildLeaderboard(guesses, pickDate);
    }

    @Test
    void allTime_delegatesToService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null, null),
                new LeaderboardService.LeaderEntry("u2", "u2", 1L, 1L, 0L, 0L, 1L, 0L, 1.0, 0, null, null, null)
        );
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildAllTimeLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }
}
