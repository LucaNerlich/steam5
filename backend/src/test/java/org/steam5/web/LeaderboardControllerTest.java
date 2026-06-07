package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LeaderboardControllerTest {

    private GuessRepository guessRepository;
    private ReviewGameStateService reviewGameStateService;
    private UserRepository userRepository;
    private SeasonService seasonService;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        userRepository = mock(UserRepository.class);
        seasonService = mock(SeasonService.class);
        cacheManager = mock(CacheManager.class);
    }

    @Test
    void today_returnsAggregatedLeaders() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, userRepository, seasonService, cacheManager);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of(new ReviewGamePick(1L, LocalDate.now(), 42L, OffsetDateTime.now())));

        Guess g1 = new Guess(1L, "u1", LocalDate.now(), 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        Guess g2 = new Guess(2L, "u1", LocalDate.now(), 2, 200L, "101-1000", "1001-10000", 3, OffsetDateTime.now());
        Guess g3 = new Guess(3L, "u2", LocalDate.now(), 1, 300L, "1001-10000", "101-1000", 1, OffsetDateTime.now());
        when(guessRepository.findAllByDate(any(LocalDate.class))).thenReturn(List.of(g1, g2, g3));

        User u1 = new User();
        u1.setSteamId("u1");
        u1.setPersonaName("User One");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u1));
        when(userRepository.findById("u2")).thenReturn(Optional.empty());

        ResponseEntity<List<LeaderboardController.LeaderEntry>> res = c.today();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        final var bodyToday = res.getBody();
        assertNotNull(bodyToday);
        assertEquals(2, bodyToday.size());
    }

    @Test
    void allTime_returnsAggregatedLeaders() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, userRepository, seasonService, cacheManager);

        // allTime() aggregates in SQL via aggregateAllTimeStats(), already ordered by
        // total points descending — not by walking raw Guess rows.
        final GuessRepository.AllTimeStatsRow r1 = mock(GuessRepository.AllTimeStatsRow.class);
        when(r1.getSteamId()).thenReturn("u1");
        when(r1.getTotalPoints()).thenReturn(5L);
        when(r1.getRounds()).thenReturn(1L);
        when(r1.getHits()).thenReturn(1L);
        when(r1.getFlops()).thenReturn(0L);
        when(r1.getTooHigh()).thenReturn(0L);
        when(r1.getTooLow()).thenReturn(0L);
        when(r1.getAvgPoints()).thenReturn(5.0);

        final GuessRepository.AllTimeStatsRow r2 = mock(GuessRepository.AllTimeStatsRow.class);
        when(r2.getSteamId()).thenReturn("u2");
        when(r2.getTotalPoints()).thenReturn(1L);
        when(r2.getRounds()).thenReturn(1L);
        when(r2.getHits()).thenReturn(0L);
        when(r2.getFlops()).thenReturn(0L);
        when(r2.getTooHigh()).thenReturn(1L);
        when(r2.getTooLow()).thenReturn(0L);
        when(r2.getAvgPoints()).thenReturn(1.0);

        when(guessRepository.aggregateAllTimeStats()).thenReturn(List.of(r1, r2));

        ResponseEntity<List<LeaderboardController.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        final var bodyAll = res.getBody();
        assertNotNull(bodyAll);
        assertEquals(2, bodyAll.size());
        assertEquals("u1", bodyAll.get(0).steamId());
        assertEquals(5L, bodyAll.get(0).totalPoints());
        assertEquals("u2", bodyAll.get(1).steamId());
    }
}


