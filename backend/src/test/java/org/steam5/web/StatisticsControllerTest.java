package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.StatisticsService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsControllerTest {

    private StatisticsService statisticsService;
    private PlayerSpotlightService playerSpotlightService;
    private LeaderboardRefreshStateRepository refreshStateRepository;
    private StatisticsController controller;

    @BeforeEach
    void setUp() {
        statisticsService = mock(StatisticsService.class);
        playerSpotlightService = mock(PlayerSpotlightService.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        controller = new StatisticsController(statisticsService, playerSpotlightService, refreshStateRepository);
    }

    @Test
    void hardestGames_setsRefreshedAtHeaderWhenStateExists() {
        List<StatisticsService.HardestGame> canned = List.of(
                new StatisticsService.HardestGame(1L, "Game", 1.5, 10L, 0.5, "over", "10000+", 4L, "1-100", java.time.LocalDate.now())
        );
        when(statisticsService.getHardestGames(25)).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 48, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.HARDEST_GAMES))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.HARDEST_GAMES, refreshedAt)));

        ResponseEntity<List<StatisticsService.HardestGame>> res = controller.hardestGames(25);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void hardestGames_noRefreshStateYet_omitsHeader() {
        when(statisticsService.getHardestGames(any(Integer.class))).thenReturn(List.of());

        ResponseEntity<List<StatisticsService.HardestGame>> res = controller.hardestGames(25);

        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void hardestGames_clampsLimitTo100() {
        when(statisticsService.getHardestGames(any(Integer.class))).thenReturn(List.of());

        controller.hardestGames(500);

        org.mockito.Mockito.verify(statisticsService).getHardestGames(eq(100));
    }
}
