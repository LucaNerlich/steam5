package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private LeaderboardRefreshStateRepository refreshStateRepository;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        seasonService = mock(SeasonService.class);
        cacheManager = mock(CacheManager.class);
        leaderboardService = mock(LeaderboardService.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        when(refreshStateRepository.findById(any(LeaderboardType.class))).thenReturn(Optional.empty());
    }

    private LeaderboardController newController() {
        return new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService, refreshStateRepository);
    }

    @Test
    void today_fetchesGuessesThenDelegatesToService() {
        LeaderboardController c = newController();
        LocalDate pickDate = LocalDate.now();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of(new ReviewGamePick(1L, pickDate, 42L, OffsetDateTime.now())));

        Guess g1 = new Guess(1L, "u1", pickDate, 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        List<Guess> guesses = List.of(g1);
        when(guessRepository.findAllByDate(pickDate)).thenReturn(guesses);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null)
        );
        when(leaderboardService.buildLeaderboard(guesses, pickDate)).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.today();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertSame(canned, res.getBody());
        verify(guessRepository).findAllByDate(pickDate);
        verify(leaderboardService).buildLeaderboard(guesses, pickDate);
        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void allTime_delegatesToServiceAndSetsRefreshedAtHeaderWhenStateExists() {
        LeaderboardController c = newController();

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null),
                new LeaderboardService.LeaderEntry("u2", "u2", 1L, 1L, 0L, 0L, 1L, 0L, 1.0, 0, null, null)
        );
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 40, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.ALL_TIME))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.ALL_TIME, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildAllTimeLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void allTime_noRefreshStateYet_omitsHeader() {
        LeaderboardController c = newController();
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();

        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void weekly_floating_delegatesToMvBackedServiceAndSetsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 7L, 2L, 1L, 0L, 0L, 1L, 3.5, 1, null, null)
        );
        when(leaderboardService.buildWeeklyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 44, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.WEEKLY))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.WEEKLY, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(true);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildWeeklyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void weekly_nonFloating_usesLiveQueryAndOmitsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());
        when(guessRepository.findAllBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(leaderboardService.buildLeaderboard(any(), any())).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(false);

        assertEquals(200, res.getStatusCode().value());
        verify(guessRepository).findAllBetween(any(LocalDate.class), any(LocalDate.class));
        verify(leaderboardService).buildLeaderboard(any(), any());
        verify(leaderboardService, never()).buildWeeklyLeaderboard(any());
        verifyNoInteractions(refreshStateRepository);
        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void monthly_delegatesToMvBackedServiceAndSetsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 10L, 2L, 1L, 0L, 0L, 1L, 5.0, 1, null, null)
        );
        when(leaderboardService.buildMonthlyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 42, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.MONTHLY))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.MONTHLY, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.monthly();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildMonthlyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void season_cacheMiss_delegatesToMvBackedServiceCachesResultAndSetsHeader() {
        LeaderboardController c = newController();
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
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null)
        );
        when(leaderboardService.buildSeasonLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 46, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.SEASON))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.SEASON, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildSeasonLeaderboard(any(LocalDate.class));
        verify(cache).put(anyString(), any());
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void season_cacheHit_skipsMvBackedServiceButStillSetsHeader() {
        LeaderboardController c = newController();
        LocalDate today = LocalDate.now();

        Season season = new Season();
        season.setSeasonNumber(3);
        season.setStartDate(today.minusDays(10));
        season.setEndDate(today.plusDays(20));
        when(seasonService.findSeasonContaining(any(LocalDate.class))).thenReturn(Optional.of(season));

        List<LeaderboardService.LeaderEntry> cached = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null)
        );
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("leaderboard-static")).thenReturn(cache);
        Cache.ValueWrapper wrapper = () -> cached;
        when(cache.get(anyString())).thenReturn(wrapper);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 46, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.SEASON))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.SEASON, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(cached, res.getBody());
        verifyNoInteractions(leaderboardService);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }
}
