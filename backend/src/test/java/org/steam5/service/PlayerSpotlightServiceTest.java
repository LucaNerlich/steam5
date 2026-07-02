package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.domain.GameDate;
import org.steam5.domain.PlayerSpotlight;
import org.steam5.domain.PlayerSpotlightInsightType;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSpotlightServiceTest {

    private GuessRepository guessRepository;
    private UserRepository userRepository;
    private StatisticsService statisticsService;
    private PlayerSpotlightRepository playerSpotlightRepository;
    private PlayerSpotlightService service;

    private final LocalDate today = GameDate.todayUtc();

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        userRepository = mock(UserRepository.class);
        statisticsService = mock(StatisticsService.class);
        playerSpotlightRepository = mock(PlayerSpotlightRepository.class);

        service = new PlayerSpotlightService(guessRepository, userRepository, statisticsService, playerSpotlightRepository);

        // Safe defaults so tiers below the one under test don't NPE on unstubbed mocks.
        when(playerSpotlightRepository.existsById(any())).thenReturn(false);
        when(statisticsService.getUserAchievementsWeekly()).thenReturn(List.of());
        when(guessRepository.findBySteamIdBetween(anyString(), any(), any())).thenReturn(List.of());
    }

    // NOTE: mocks referenced by an outer when(...).thenReturn(...) must be fully built
    // *before* that call — building them inline as constructor/method arguments corrupts
    // Mockito's ongoing-stubbing state (the outer when()'s target gets lost).
    private GuessRepository.AllTimeStatsRow allTimeRow(String steamId, long rounds, double avgPoints) {
        final GuessRepository.AllTimeStatsRow row = mock(GuessRepository.AllTimeStatsRow.class);
        when(row.getSteamId()).thenReturn(steamId);
        when(row.getRounds()).thenReturn(rounds);
        when(row.getAvgPoints()).thenReturn(avgPoints);
        return row;
    }

    private GuessRepository.UserDateRow dateRow(String steamId, LocalDate date) {
        final GuessRepository.UserDateRow row = mock(GuessRepository.UserDateRow.class);
        when(row.getSteamId()).thenReturn(steamId);
        when(row.getGameDate()).thenReturn(date);
        return row;
    }

    /** dates descending, most recent first — matches findDistinctDatesUpToForUsers ordering. */
    private List<GuessRepository.UserDateRow> consecutiveDaysEnding(String steamId, LocalDate lastDate, int count) {
        final List<GuessRepository.UserDateRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(dateRow(steamId, lastDate.minusDays(i)));
        }
        return rows;
    }

    private void stubAllTimeStats(GuessRepository.AllTimeStatsRow... rows) {
        when(guessRepository.aggregateAllTimeStats()).thenReturn(List.of(rows));
    }

    @Test
    void excludesPlayersBelowRoundThreshold() {
        final GuessRepository.AllTimeStatsRow belowThreshold = allTimeRow("belowThreshold", 60, 2.0);
        final GuessRepository.AllTimeStatsRow eligible = allTimeRow("eligible", 100, 2.0);
        stubAllTimeStats(belowThreshold, eligible);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("belowThreshold", today, 1));
        allDates.addAll(consecutiveDaysEnding("eligible", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("eligible", captor.getValue().getSteamId());
    }

    @Test
    void excludesPlayersNotActiveInLastTwoWeeks() {
        final GuessRepository.AllTimeStatsRow stale = allTimeRow("stale", 200, 2.0);
        final GuessRepository.AllTimeStatsRow active = allTimeRow("active", 100, 2.0);
        stubAllTimeStats(stale, active);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("stale", today.minusDays(20), 1)); // last played 20 days ago
        allDates.addAll(consecutiveDaysEnding("active", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("active", captor.getValue().getSteamId());
    }

    @Test
    void dayStreakTierOutranksMilestoneFallback() {
        final GuessRepository.AllTimeStatsRow streaker = allTimeRow("streaker", 100, 2.0);
        final GuessRepository.AllTimeStatsRow oneAndDone = allTimeRow("oneAndDone", 100, 2.0);
        stubAllTimeStats(streaker, oneAndDone);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("streaker", today, 6)); // active 6-day streak, >= MIN_DAY_STREAK
        allDates.addAll(consecutiveDaysEnding("oneAndDone", today, 1)); // only played today — milestone tier only
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("streaker", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.DAY_STREAK, captor.getValue().getInsightType());
    }

    @Test
    void fallsBackToMilestoneWhenNoOneHasANotableStory() {
        final GuessRepository.AllTimeStatsRow onlySteadyPlayer = allTimeRow("onlySteadyPlayer", 90, 2.5);
        stubAllTimeStats(onlySteadyPlayer);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("onlySteadyPlayer", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("onlySteadyPlayer", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.MILESTONE, captor.getValue().getInsightType());
    }

    @Test
    void doesNothingWhenNoOneIsEligible() {
        final GuessRepository.AllTimeStatsRow tooFewRounds = allTimeRow("tooFewRounds", 10, 2.0);
        stubAllTimeStats(tooFewRounds);

        service.computeAndPersistForToday();

        verify(playerSpotlightRepository, never()).save(any());
    }

    @Test
    void skipsRecomputeWhenAlreadyPersistedForToday() {
        when(playerSpotlightRepository.existsById(today)).thenReturn(true);

        service.computeAndPersistForToday();

        verify(guessRepository, never()).aggregateAllTimeStats();
        verify(playerSpotlightRepository, never()).save(any());
    }

    @Test
    void tieBreakAmongEqualTierCandidatesIsDeterministicForTheDay() {
        final GuessRepository.AllTimeStatsRow playerA = allTimeRow("playerA", 90, 2.0);
        final GuessRepository.AllTimeStatsRow playerB = allTimeRow("playerB", 90, 2.0);
        stubAllTimeStats(playerA, playerB);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("playerA", today, 1));
        allDates.addAll(consecutiveDaysEnding("playerB", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        service.computeAndPersistForToday();
        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        final String picked = captor.getValue().getSteamId();

        // Both candidates are tied in the MILESTONE tier — the winner must match the
        // documented tie-break: sort by steamId, then a Random seeded by the epoch day.
        final List<String> sorted = List.of("playerA", "playerB").stream().sorted().toList();
        final int expectedIndex = new Random(today.toEpochDay()).nextInt(sorted.size());
        assertEquals(sorted.get(expectedIndex), picked);
    }
}
