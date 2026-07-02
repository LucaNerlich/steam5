package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.PlayerSpotlight;
import org.steam5.domain.PlayerSpotlightInsightType;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        when(guessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc(anyString())).thenReturn(List.of());
        when(guessRepository.findRoundAvgScoresInRange(any(), any())).thenReturn(List.of());
        when(guessRepository.findAllForDay(anyString(), any())).thenReturn(List.of());
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

    private Guess guess(String steamId, LocalDate date, int points) {
        final Guess g = new Guess();
        g.setSteamId(steamId);
        g.setGameDate(date);
        g.setPoints(points);
        return g;
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

    @Test
    void bestDayEverTierWinsWhenYesterdayIsANewPersonalRecord() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow recordBreaker = allTimeRow("recordBreaker", 100, 2.0);
        stubAllTimeStats(recordBreaker);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("recordBreaker", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final List<Guess> history = List.of(
                guess("recordBreaker", yesterday.minusDays(10), 8),
                guess("recordBreaker", yesterday.minusDays(5), 12),
                guess("recordBreaker", yesterday, 24)
        );
        when(guessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc("recordBreaker")).thenReturn(history);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("recordBreaker", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.BEST_DAY_EVER, captor.getValue().getInsightType());
    }

    @Test
    void beatTheOddsTierWinsWhenCandidateAcedTheHardestRoundOfTheDay() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow oddsBeater = allTimeRow("oddsBeater", 100, 2.0);
        stubAllTimeStats(oddsBeater);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("oddsBeater", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final GuessRepository.RoundAvgScoreRow hardRound = mock(GuessRepository.RoundAvgScoreRow.class);
        when(hardRound.getGameDate()).thenReturn(yesterday);
        when(hardRound.getRoundIndex()).thenReturn(3);
        when(hardRound.getAvgScore()).thenReturn(1.2);
        when(hardRound.getPlayerCount()).thenReturn(20L);
        when(guessRepository.findRoundAvgScoresInRange(yesterday, yesterday)).thenReturn(List.of(hardRound));

        final Guess theirGuess = guess("oddsBeater", yesterday, 5);
        theirGuess.setRoundIndex(3);
        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("oddsBeater", yesterday, 3))
                .thenReturn(Optional.of(theirGuess));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("oddsBeater", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.BEAT_THE_ODDS, captor.getValue().getInsightType());
    }

    @Test
    void welcomeBackTierWinsWhenCandidateReturnedAfterAGapAndPlayedWell() {
        final LocalDate mostRecent = today.minusDays(1);
        final LocalDate beforeGap = mostRecent.minusDays(6); // gap of 6 days, >= the 4-day threshold

        final GuessRepository.AllTimeStatsRow returner = allTimeRow("returner", 100, 2.0);
        stubAllTimeStats(returner);

        final List<GuessRepository.UserDateRow> dates = new ArrayList<>();
        dates.add(dateRow("returner", mostRecent));
        dates.add(dateRow("returner", beforeGap));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        when(guessRepository.findAllForDay("returner", mostRecent)).thenReturn(List.of(
                guess("returner", mostRecent, 4),
                guess("returner", mostRecent, 3)
        ));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("returner", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.WELCOME_BACK, captor.getValue().getInsightType());
    }

    @Test
    void mostImprovedTierWinsWhenRecentFormIsClearlyBetterThanBefore() {
        final GuessRepository.AllTimeStatsRow improver = allTimeRow("improver", 100, 2.0);
        stubAllTimeStats(improver);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("improver", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today)))
                .thenReturn(dates);

        final LocalDate last30Start = today.minusDays(30);
        final LocalDate last30End = today.minusDays(1);
        final LocalDate prior30Start = today.minusDays(60);
        final LocalDate prior30End = today.minusDays(31);

        final List<Guess> last30 = new ArrayList<>();
        for (int i = 0; i < 15; i++) last30.add(guess("improver", last30Start.plusDays(i), 4));
        final List<Guess> prior30 = new ArrayList<>();
        for (int i = 0; i < 15; i++) prior30.add(guess("improver", prior30Start.plusDays(i), 2));

        when(guessRepository.findBySteamIdBetween("improver", last30Start, last30End)).thenReturn(last30);
        when(guessRepository.findBySteamIdBetween("improver", prior30Start, prior30End)).thenReturn(prior30);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("improver", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.MOST_IMPROVED, captor.getValue().getInsightType());
    }
}
