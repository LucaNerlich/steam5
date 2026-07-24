package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.domain.Guess;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    private GuessRepository guessRepository;
    private UserRepository userRepository;
    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        userRepository = mock(UserRepository.class);
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new LeaderboardService(guessRepository, userRepository, leaderboardMvRepository);
    }

    @Test
    void buildLeaderboard_returnsAggregatedLeaders() {
        LocalDate asOfDate = LocalDate.now();
        Guess g1 = new Guess(1L, "u1", asOfDate, 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        Guess g2 = new Guess(2L, "u1", asOfDate, 2, 200L, "101-1000", "1001-10000", 3, OffsetDateTime.now());
        Guess g3 = new Guess(3L, "u2", asOfDate, 1, 300L, "1001-10000", "101-1000", 1, OffsetDateTime.now());
        List<Guess> guesses = List.of(g1, g2, g3);

        User u1 = new User();
        u1.setSteamId("u1");
        u1.setPersonaName("User One");
        when(userRepository.findAllById(any())).thenReturn(List.of(u1));

        List<LeaderboardService.LeaderEntry> result = service.buildLeaderboard(guesses, asOfDate);

        assertEquals(2, result.size());

        LeaderboardService.LeaderEntry first = result.get(0);
        assertEquals("u1", first.steamId());
        assertEquals("User One", first.personaName());
        assertEquals(8L, first.totalPoints());
        assertEquals(2L, first.rounds());
        assertEquals(1L, first.hits());
        assertEquals(0L, first.flops());
        assertEquals(0L, first.tooHigh());
        assertEquals(1L, first.tooLow());
        assertEquals(4.0, first.avgPoints());
        assertEquals(0, first.streak());

        LeaderboardService.LeaderEntry second = result.get(1);
        assertEquals("u2", second.steamId());
        assertEquals("u2", second.personaName()); // no User record found — falls back to steamId
        assertEquals(1L, second.totalPoints());
        assertEquals(1L, second.rounds());
        assertEquals(0L, second.hits());
        assertEquals(1L, second.tooHigh());
        assertEquals(0L, second.tooLow());
        assertEquals(1.0, second.avgPoints());
    }

    @Test
    void buildLeaderboard_emptyGuesses_returnsEmptyList() {
        assertEquals(List.of(), service.buildLeaderboard(List.of(), LocalDate.now()));
    }

    @Test
    void buildAllTimeLeaderboard_returnsAggregatedLeaders() {
        // all-time aggregates come from mv_leaderboard_all_time, pre-ordered by total points
        // descending (see mv-leaderboard-all-time.sql) — not walked from raw Guess rows.
        final LeaderboardMvRepository.LeaderboardMvRow r1 = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(r1.getSteamId()).thenReturn("u1");
        when(r1.getTotalPoints()).thenReturn(5L);
        when(r1.getRounds()).thenReturn(1L);
        when(r1.getHits()).thenReturn(1L);
        when(r1.getFlops()).thenReturn(0L);
        when(r1.getTooHigh()).thenReturn(0L);
        when(r1.getTooLow()).thenReturn(0L);
        when(r1.getAvgPoints()).thenReturn(5.0);
        when(r1.getPersonaName()).thenReturn("User One");

        final LeaderboardMvRepository.LeaderboardMvRow r2 = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(r2.getSteamId()).thenReturn("u2");
        when(r2.getTotalPoints()).thenReturn(1L);
        when(r2.getRounds()).thenReturn(1L);
        when(r2.getHits()).thenReturn(0L);
        when(r2.getFlops()).thenReturn(0L);
        when(r2.getTooHigh()).thenReturn(1L);
        when(r2.getTooLow()).thenReturn(0L);
        when(r2.getAvgPoints()).thenReturn(1.0);
        // r2.getPersonaName() intentionally left unstubbed (null) — exercises the steamId fallback

        when(leaderboardMvRepository.findAllTime()).thenReturn(List.of(r1, r2));
        // findDistinctDatesUpToForUsers intentionally left unstubbed —
        // Mockito's empty-list default exercises the null-safe streak fallback path.

        List<LeaderboardService.LeaderEntry> result = service.buildAllTimeLeaderboard(LocalDate.now());

        assertEquals(2, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals("User One", result.get(0).personaName());
        assertEquals(5L, result.get(0).totalPoints());
        assertEquals(0, result.get(0).streak());
        assertEquals("u2", result.get(1).steamId());
        assertEquals("u2", result.get(1).personaName());
    }

    @Test
    void buildAllTimeLeaderboard_noRows_returnsEmptyList() {
        when(leaderboardMvRepository.findAllTime()).thenReturn(List.of());
        assertEquals(List.of(), service.buildAllTimeLeaderboard(LocalDate.now()));
    }

    @Test
    void buildMonthlyLeaderboard_delegatesToMonthlyMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(10L);
        when(row.getRounds()).thenReturn(2L);
        when(row.getHits()).thenReturn(1L);
        when(row.getFlops()).thenReturn(0L);
        when(row.getTooHigh()).thenReturn(0L);
        when(row.getTooLow()).thenReturn(1L);
        when(row.getAvgPoints()).thenReturn(5.0);

        when(leaderboardMvRepository.findMonthly()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildMonthlyLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(10L, result.get(0).totalPoints());
    }

    @Test
    void buildWeeklyLeaderboard_delegatesToWeeklyMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(7L);
        when(row.getRounds()).thenReturn(2L);
        when(row.getHits()).thenReturn(1L);
        when(row.getFlops()).thenReturn(0L);
        when(row.getTooHigh()).thenReturn(1L);
        when(row.getTooLow()).thenReturn(0L);
        when(row.getAvgPoints()).thenReturn(3.5);

        when(leaderboardMvRepository.findWeekly()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildWeeklyLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(7L, result.get(0).totalPoints());
    }

    @Test
    void buildSeasonLeaderboard_delegatesToSeasonMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(20L);
        when(row.getRounds()).thenReturn(4L);
        when(row.getHits()).thenReturn(2L);
        when(row.getFlops()).thenReturn(1L);
        when(row.getTooHigh()).thenReturn(1L);
        when(row.getTooLow()).thenReturn(0L);
        when(row.getAvgPoints()).thenReturn(5.0);

        when(leaderboardMvRepository.findSeason()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildSeasonLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(20L, result.get(0).totalPoints());
    }
}
