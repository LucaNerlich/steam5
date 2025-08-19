package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LeaderboardControllerTest {

    private GuessRepository guessRepository;
    private ReviewGameStateService reviewGameStateService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        userRepository = mock(UserRepository.class);
    }

    @Test
    void today_returnsAggregatedLeaders() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, userRepository);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of(new ReviewGamePick(1L, LocalDate.now(), 42L, OffsetDateTime.now())));

        Guess g1 = new Guess(1L, "u1", LocalDate.now(), 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        Guess g2 = new Guess(2L, "u1", LocalDate.now(), 2, 200L, "101-1000", "1001-10000", 3, OffsetDateTime.now());
        Guess g3 = new Guess(3L, "u2", LocalDate.now(), 1, 300L, "1001-10000", "101-1000", 1, OffsetDateTime.now());
        when(guessRepository.findAllByDate(any(LocalDate.class))).thenReturn(List.of(g1, g2, g3));

        User u1 = new User();
        u1.setSteamId("u1");
        u1.setPersonaName("User One");
        when(userRepository.findById("u1")).thenReturn(java.util.Optional.of(u1));
        when(userRepository.findById("u2")).thenReturn(java.util.Optional.empty());

        ResponseEntity<List<LeaderboardController.LeaderEntry>> res = c.today();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        final var bodyToday = res.getBody();
        assertNotNull(bodyToday);
        assertEquals(2, bodyToday.size());
        assertTrue(bodyToday.stream().anyMatch(e -> e.steamId().equals("u1") && e.totalPoints() == 8));
    }

    @Test
    void allTime_returnsAggregatedLeaders() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, userRepository);
        Guess g1 = new Guess(1L, "u1", LocalDate.now(), 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        Guess g2 = new Guess(3L, "u2", LocalDate.now(), 1, 300L, "1001-10000", "101-1000", 1, OffsetDateTime.now());
        when(guessRepository.findAll()).thenReturn(List.of(g1, g2));

        ResponseEntity<List<LeaderboardController.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        final var bodyAll = res.getBody();
        assertNotNull(bodyAll);
        assertEquals(2, bodyAll.size());
    }
}


