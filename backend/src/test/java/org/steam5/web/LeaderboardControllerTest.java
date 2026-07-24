package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void weekly_floating_delegatesToMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 7L, 2L, 1L, 0L, 0L, 1L, 3.5, 1, null, null, null)
        );
        when(leaderboardService.buildWeeklyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(true);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildWeeklyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }

    @Test
    void weekly_nonFloating_usesLiveQueryNotMv() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());
        when(guessRepository.findAllBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(leaderboardService.buildLeaderboard(any(), any())).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(false);

        assertEquals(200, res.getStatusCode().value());
        verify(guessRepository).findAllBetween(any(LocalDate.class), any(LocalDate.class));
        verify(leaderboardService).buildLeaderboard(any(), any());
        verify(leaderboardService, never()).buildWeeklyLeaderboard(any());
    }

    @Test
    void monthly_delegatesToMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 10L, 2L, 1L, 0L, 0L, 1L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildMonthlyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.monthly();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildMonthlyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }

    @Test
    void season_cacheMiss_delegatesToMvBackedServiceAndCachesResult() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate today = LocalDate.now();

        Season season = new Season();
        season.setSeasonNumber(3);
        season.setStartDate(today.minusDays(10));
        season.setEndDate(today.plusDays(20));
        when(seasonService.findSeasonContaining(any(LocalDate.class))).thenReturn(Optional.of(season));

        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("leaderboard-static")).thenReturn(cache);
        when(cache.get(anyString())).thenReturn(null);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildSeasonLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildSeasonLeaderboard(any(LocalDate.class));
        verify(cache).put(anyString(), any());
    }

    @Test
    void season_cacheHit_skipsMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate today = LocalDate.now();

        Season season = new Season();
        season.setSeasonNumber(3);
        season.setStartDate(today.minusDays(10));
        season.setEndDate(today.plusDays(20));
        when(seasonService.findSeasonContaining(any(LocalDate.class))).thenReturn(Optional.of(season));

        List<LeaderboardService.LeaderEntry> cached = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null, null)
        );
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("leaderboard-static")).thenReturn(cache);
        Cache.ValueWrapper wrapper = () -> cached;
        when(cache.get(anyString())).thenReturn(wrapper);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(cached, res.getBody());
        verifyNoInteractions(leaderboardService);
    }
}
