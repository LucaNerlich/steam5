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
        when(guessRepository.findBySteamIdIn(anyList())).thenReturn(List.of());
        when(guessRepository.findRoundAvgScoresInRange(any(), any())).thenReturn(List.of());
        when(guessRepository.findByGameDateAndRoundIndex(any(), anyInt())).thenReturn(List.of());
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
        when(guessRepository.findBySteamIdIn(anyList())).thenReturn(history);

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
        when(guessRepository.findByGameDateAndRoundIndex(yesterday, 3)).thenReturn(List.of(theirGuess));

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

        when(guessRepository.findBySteamIdIn(anyList())).thenReturn(List.of(
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
        final LocalDate prior30Start = today.minusDays(60);

        final List<Guess> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) history.add(guess("improver", last30Start.plusDays(i), 4));
        for (int i = 0; i < 15; i++) history.add(guess("improver", prior30Start.plusDays(i), 2));
        when(guessRepository.findBySteamIdIn(anyList())).thenReturn(history);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("improver", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.MOST_IMPROVED, captor.getValue().getInsightType());
    }

    @Test
    void lotteryPicksAmongMultipleQualifyingCompetitiveTiers() {
        // Two candidates, each qualifying for a *different* competitive tier at the
        // same time: "streaker" qualifies for DAY_STREAK (reusing the fixture from
        // dayStreakTierOutranksMilestoneFallback) and "improver" qualifies for
        // MOST_IMPROVED (reusing the fixture from
        // mostImprovedTierWinsWhenRecentFormIsClearlyBetterThanBefore). This is the
        // first test in the suite where qualifying.size() > 1, so it's the first to
        // actually exercise the random draw in compute() rather than the
        // single-candidate short-circuit every other tier test relies on.
        final GuessRepository.AllTimeStatsRow streaker = allTimeRow("streaker", 100, 2.0);
        final GuessRepository.AllTimeStatsRow improver = allTimeRow("improver", 100, 2.0);
        stubAllTimeStats(streaker, improver);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("streaker", today, 6)); // active 6-day streak, >= MIN_DAY_STREAK
        allDates.addAll(consecutiveDaysEnding("improver", today, 1)); // only played today — too short for DAY_STREAK
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        final LocalDate last30Start = today.minusDays(30);
        final LocalDate prior30Start = today.minusDays(60);

        final List<Guess> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) history.add(guess("improver", last30Start.plusDays(i), 4));
        for (int i = 0; i < 15; i++) history.add(guess("improver", prior30Start.plusDays(i), 2));
        when(guessRepository.findBySteamIdIn(anyList())).thenReturn(history);

        // Neither candidate spuriously qualifies for the other's tier or for
        // BEST_DAY_EVER / BEAT_THE_ODDS / WELCOME_BACK / HOT_STREAK:
        // - "streaker" has no entries in the stubbed findBySteamIdIn(...) result (only
        //   "improver" does), so historyByPlayer.getOrDefault("streaker", ...) is always
        //   empty — it never clears any tier's round-count floor.
        // - "improver" has only a single date in datesDesc, so its current streak is
        //   1 (< MIN_DAY_STREAK) and it has no second date for WELCOME_BACK's gap check;
        //   its stubbed history also falls entirely outside HOT_STREAK's 14-day window.
        // - BEAT_THE_ODDS relies on findRoundAvgScoresInRange, left at setUp()'s default
        //   empty-list stub.
        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());

        // Independently reproduce the documented lottery: qualifying tiers are collected
        // in compute()'s fixed evaluation order (DAY_STREAK before MOST_IMPROVED here),
        // then one is drawn via new Random(today.toEpochDay()).nextInt(qualifying.size()).
        final List<PlayerSpotlightInsightType> qualifyingInOrder =
                List.of(PlayerSpotlightInsightType.DAY_STREAK, PlayerSpotlightInsightType.MOST_IMPROVED);
        final int expectedIndex = new Random(today.toEpochDay()).nextInt(qualifyingInOrder.size());
        final PlayerSpotlightInsightType expectedType = qualifyingInOrder.get(expectedIndex);
        final String expectedSteamId =
                expectedType == PlayerSpotlightInsightType.DAY_STREAK ? "streaker" : "improver";

        assertEquals(expectedType, captor.getValue().getInsightType());
        assertEquals(expectedSteamId, captor.getValue().getSteamId());
    }

    @Test
    void listSpotlightsForPlayerMapsPersistedEntitiesToHistoryEntries() {
        final PlayerSpotlight yesterday = new PlayerSpotlight();
        yesterday.setGameDate(today.minusDays(1));
        yesterday.setSteamId("historyPlayer");
        yesterday.setInsightType(PlayerSpotlightInsightType.HOT_STREAK);
        yesterday.setHeadline("In red-hot form!");
        yesterday.setDetail("Averaging 4.0 pts/round over the last 14 days — well above their usual 2.0.");
        yesterday.setStatLabel("Recent avg");
        yesterday.setStatValue(4.0);

        when(playerSpotlightRepository.findTop10BySteamIdOrderByGameDateDesc("historyPlayer"))
                .thenReturn(List.of(yesterday));

        final List<PlayerSpotlightService.SpotlightHistoryEntry> history =
                service.listSpotlightsForPlayer("historyPlayer");

        assertEquals(1, history.size());
        final PlayerSpotlightService.SpotlightHistoryEntry entry = history.get(0);
        assertEquals(today.minusDays(1), entry.gameDate());
        assertEquals(PlayerSpotlightInsightType.HOT_STREAK, entry.insightType());
        assertEquals("In red-hot form!", entry.headline());
        assertEquals(4.0, entry.statValue());
    }

    @Test
    void fallsBackToWeeklyAchievementWhenNoCompetitiveTierQualifies() {
        final GuessRepository.AllTimeStatsRow achiever = allTimeRow("achiever", 100, 2.0);
        stubAllTimeStats(achiever);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("achiever", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final StatisticsService.UserLabel label = new StatisticsService.UserLabel(
                "achiever", StatisticsService.UserAchievement.SHARPSHOOTER, null, 4.2, null, null, null);
        when(statisticsService.getUserAchievementsWeekly()).thenReturn(List.of(label));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("achiever", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.WEEKLY_ACHIEVEMENT, captor.getValue().getInsightType());
    }

    @Test
    void milestoneTierUsesNiceNumberFramingWhenRoundsAreCloseToAMilestone() {
        final GuessRepository.AllTimeStatsRow almostCentury = mock(GuessRepository.AllTimeStatsRow.class);
        when(almostCentury.getSteamId()).thenReturn("almostCentury");
        when(almostCentury.getRounds()).thenReturn(98L);
        when(almostCentury.getAvgPoints()).thenReturn(2.0);
        when(almostCentury.getTotalPoints()).thenReturn(400L);
        stubAllTimeStats(almostCentury);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("almostCentury", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today)))
                .thenReturn(dates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals(PlayerSpotlightInsightType.MILESTONE, captor.getValue().getInsightType());
        assertEquals("Closing in on 100 rounds — only 2 away!", captor.getValue().getDetail());
    }

    @Test
    void milestoneTierUsesNiceNumberFramingWhenLifetimePointsAreCloseToAMilestone() {
        // Rounds (150) sit far from any ROUND_MILESTONES entry, so this only passes
        // if the lifetime-points branch (checked via .or(...) after the rounds check)
        // is actually reached and correctly formatted.
        final GuessRepository.AllTimeStatsRow almostThousandPoints = mock(GuessRepository.AllTimeStatsRow.class);
        when(almostThousandPoints.getSteamId()).thenReturn("almostThousandPoints");
        when(almostThousandPoints.getRounds()).thenReturn(150L);
        when(almostThousandPoints.getAvgPoints()).thenReturn(2.0);
        when(almostThousandPoints.getTotalPoints()).thenReturn(995L);
        stubAllTimeStats(almostThousandPoints);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("almostThousandPoints", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today)))
                .thenReturn(dates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals(PlayerSpotlightInsightType.MILESTONE, captor.getValue().getInsightType());
        assertEquals("Closing in on 1,000 lifetime points — only 5 away!", captor.getValue().getDetail());
    }
}
