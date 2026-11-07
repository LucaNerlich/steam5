package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    private static final String STEAM_ID = "76561198000000001";

    private UserRepository userRepository;
    private GuessRepository guessRepository;
    private SteamAppIndexRepository appIndexRepository;
    private SeasonService seasonService;
    private PlayerSpotlightService playerSpotlightService;
    private ProfileController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        guessRepository = mock(GuessRepository.class);
        appIndexRepository = mock(SteamAppIndexRepository.class);
        seasonService = mock(SeasonService.class);
        playerSpotlightService = mock(PlayerSpotlightService.class);
        controller = new ProfileController(
                userRepository,
                guessRepository,
                appIndexRepository,
                seasonService,
                playerSpotlightService
        );
    }

    @Test
    void getProfile_omitsTodayActualBucketAndPoints_butKeepsPastDays() {
        final LocalDate today = GameDate.todayUtc();
        final LocalDate yesterday = today.minusDays(1);

        final User user = new User();
        user.setSteamId(STEAM_ID);
        user.setPersonaName("Alice");
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

        final Guess todayGuess = guess(STEAM_ID, today, 1, 570L, "10000+", "1-100", 4);
        final Guess yesterdayGuess = guess(STEAM_ID, yesterday, 1, 730L, "1000-5000", "1000-5000", 10);
        when(guessRepository.findRecentBySteamIdOrderByGameDateDescRoundIndexAsc(eq(STEAM_ID), any(), any()))
                .thenReturn(List.of(todayGuess, yesterdayGuess));
        final GuessRepository.UserStatsRow statsRow = userStats(14L, 2L, 1L, 0L, 1L, 7.0);
        final GuessRepository.PlayerStreakRow streakRow = streakStats(2, 2);
        when(guessRepository.findAllTimeStatsForUser(STEAM_ID)).thenReturn(List.of(statsRow));
        when(guessRepository.findPlayerStreakStats(anyList(), any())).thenReturn(List.of(streakRow));

        when(appIndexRepository.findAllById(List.of(570L, 730L))).thenReturn(List.of(
                new SteamAppIndex(570L, "Dota 2"),
                new SteamAppIndex(730L, "Counter-Strike 2")
        ));
        when(seasonService.listAwardsForPlayer(STEAM_ID)).thenReturn(List.of());
        when(playerSpotlightService.listSpotlightsForPlayer(STEAM_ID)).thenReturn(List.of());

        final ResponseEntity<?> response = controller.getProfile(STEAM_ID);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        final Map<String, Object> body = (Map<String, Object>) response.getBody();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> days = (List<Map<String, Object>>) body.get("days");
        assertEquals(2, days.size());

        @SuppressWarnings("unchecked")
        final Map<String, Object> todayDay = days.stream()
                .filter(day -> today.toString().equals(day.get("date")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        final Map<String, Object> todayRound = ((List<Map<String, Object>>) todayDay.get("rounds")).getFirst();
        assertEquals(1, todayRound.get("roundIndex"));
        assertEquals("10000+", todayRound.get("selectedBucket"));
        assertFalse(todayRound.containsKey("actualBucket"));
        assertFalse(todayRound.containsKey("points"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> yesterdayDay = days.stream()
                .filter(day -> yesterday.toString().equals(day.get("date")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        final Map<String, Object> yesterdayRound = ((List<Map<String, Object>>) yesterdayDay.get("rounds")).getFirst();
        assertEquals("1000-5000", yesterdayRound.get("actualBucket"));
        assertEquals(10, yesterdayRound.get("points"));
        assertTrue(yesterdayRound.containsKey("actualBucket"));
        assertTrue(yesterdayRound.containsKey("points"));
    }

    @Test
    void getProfile_usesFullHistoryStreakStatsWhileTheDaysPayloadStaysBounded() {
        final LocalDate today = GameDate.todayUtc();

        final User user = new User();
        user.setSteamId(STEAM_ID);
        user.setPersonaName("Alice");
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

        // Only the display window is loaded, but it is already capped at 365 days.
        final List<Guess> guesses = IntStream.range(0, 365)
                .mapToObj(offset -> guess(STEAM_ID, today.minusDays(offset), 1,
                        570L, "1-100", "1-100", 5))
                .toList();
        when(guessRepository.findRecentBySteamIdOrderByGameDateDescRoundIndexAsc(
                eq(STEAM_ID), eq(today.minusDays(364)), eq(today)))
                .thenReturn(guesses);
        final GuessRepository.UserStatsRow statsRow =
                userStats(2000L, 400L, 200L, 100L, 100L, 5.0);
        // A 400-day streak: longer than the days payload the client receives.
        final GuessRepository.PlayerStreakRow streakRow = streakStats(400, 400);
        when(guessRepository.findAllTimeStatsForUser(STEAM_ID)).thenReturn(List.of(statsRow));
        when(guessRepository.findPlayerStreakStats(anyList(), eq(today))).thenReturn(List.of(streakRow));
        when(appIndexRepository.findAllById(List.of(570L)))
                .thenReturn(List.of(new SteamAppIndex(570L, "Dota 2")));
        when(seasonService.listAwardsForPlayer(STEAM_ID)).thenReturn(List.of());
        when(playerSpotlightService.listSpotlightsForPlayer(STEAM_ID)).thenReturn(List.of());

        final ResponseEntity<?> response = controller.getProfile(STEAM_ID);

        @SuppressWarnings("unchecked")
        final Map<String, Object> body = (Map<String, Object>) response.getBody();
        @SuppressWarnings("unchecked")
        final Map<String, Object> streaks = (Map<String, Object>) body.get("streaks");
        assertEquals(400, ((Number) streaks.get("current")).intValue());
        assertEquals(400L, ((Number) streaks.get("longest")).longValue());

        @SuppressWarnings("unchecked")
        final Map<String, Object> stats = (Map<String, Object>) body.get("stats");
        assertEquals(2000L, ((Number) stats.get("totalPoints")).longValue());
        assertEquals(400L, ((Number) stats.get("rounds")).longValue());

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> days = (List<Map<String, Object>>) body.get("days");
        assertEquals(365, days.size(), "the display payload stays bounded to 365 days");
    }

    private static GuessRepository.UserStatsRow userStats(long totalPoints, long rounds, long hits,
                                                          long tooHigh, long tooLow, double avgPoints) {
        final GuessRepository.UserStatsRow row = mock(GuessRepository.UserStatsRow.class);
        when(row.getTotalPoints()).thenReturn(totalPoints);
        when(row.getRounds()).thenReturn(rounds);
        when(row.getHits()).thenReturn(hits);
        when(row.getTooHigh()).thenReturn(tooHigh);
        when(row.getTooLow()).thenReturn(tooLow);
        when(row.getAvgPoints()).thenReturn(avgPoints);
        return row;
    }

    private static GuessRepository.PlayerStreakRow streakStats(int current, int longest) {
        final GuessRepository.PlayerStreakRow row = mock(GuessRepository.PlayerStreakRow.class);
        when(row.getCurrentStreak()).thenReturn(current);
        when(row.getLongestStreak()).thenReturn(longest);
        return row;
    }

    private static Guess guess(
            String steamId,
            LocalDate gameDate,
            int roundIndex,
            long appId,
            String selectedBucket,
            String actualBucket,
            int points
    ) {
        final Guess guess = new Guess();
        guess.setSteamId(steamId);
        guess.setGameDate(gameDate);
        guess.setRoundIndex(roundIndex);
        guess.setAppId(appId);
        guess.setSelectedBucket(selectedBucket);
        guess.setActualBucket(actualBucket);
        guess.setPoints(points);
        guess.setCreatedAt(OffsetDateTime.now());
        return guess;
    }
}
