package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.domain.Comment;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.PlayerSpotlight;
import org.steam5.domain.PlayerSpotlightInsightType;
import org.steam5.domain.ReactionType;
import org.steam5.repository.CommentReactionRepository;
import org.steam5.repository.CommentRepository;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private CommentRepository commentRepository;
    private CommentReactionRepository commentReactionRepository;
    private PlayerSpotlightService service;

    private final LocalDate today = GameDate.todayUtc();

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        userRepository = mock(UserRepository.class);
        statisticsService = mock(StatisticsService.class);
        playerSpotlightRepository = mock(PlayerSpotlightRepository.class);
        commentRepository = mock(CommentRepository.class);
        commentReactionRepository = mock(CommentReactionRepository.class);

        service = new PlayerSpotlightService(
                guessRepository,
                userRepository,
                statisticsService,
                playerSpotlightRepository,
                commentRepository,
                commentReactionRepository
        );

        // Safe defaults so tiers below the one under test don't NPE on unstubbed mocks.
        when(playerSpotlightRepository.existsById(any())).thenReturn(false);
        when(statisticsService.getUserAchievementsWeekly()).thenReturn(List.of());
        when(guessRepository.findBySteamIdBetween(anyString(), any(), any())).thenReturn(List.of());
        when(guessRepository.findDailyTotalsBySteamIdIn(anyList())).thenReturn(List.of());
        when(guessRepository.findRoundAvgScoresInRange(any(), any())).thenReturn(List.of());
        when(guessRepository.findByGameDateAndRoundIndex(any(), anyInt())).thenReturn(List.of());
        when(commentRepository.findTopReactedCommentId(any())).thenReturn(Optional.empty());
        when(commentReactionRepository.countByCommentIds(anyList())).thenReturn(List.of());
        // Default: every requested steamId gets enough (35) low-signal rounds in the recency
        // window to clear the activity floor. Wider history-prefetch windows (~60d) default to
        // empty so tier tests that need specific history stub via stubRecentHistory(...).
        // Use doAnswer (not when().thenAnswer) so re-stubbing in individual tests does not
        // re-enter this answer with null matcher args.
        doAnswer(invocation -> {
            final List<String> steamIds = invocation.getArgument(0);
            final LocalDate start = invocation.getArgument(1);
            final LocalDate end = invocation.getArgument(2);
            final long spanDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            if (spanDays > 20) {
                return List.<Guess>of();
            }
            final List<Guess> rows = new ArrayList<>();
            for (final String steamId : steamIds) {
                for (int i = 0; i < 35; i++) {
                    rows.add(guess(steamId, today, 2));
                }
            }
            return rows;
        }).when(guessRepository).findBySteamIdInAndGameDateBetween(anyList(), any(), any());
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
        when(guessRepository.aggregateAllTimeStatsHavingMinRounds(anyLong())).thenAnswer(invocation -> {
            final long minRounds = invocation.getArgument(0);
            return java.util.Arrays.stream(rows)
                    .filter(row -> row.getRounds() != null && row.getRounds() >= minRounds)
                    .toList();
        });
    }

    /**
     * Stubs the wider (~60d) history prefetch used by MOST_IMPROVED / WELCOME_BACK / HOT_STREAK
     * while keeping the default 14-day recency activity floor intact.
     */
    private void stubRecentHistory(List<Guess> history) {
        doAnswer(invocation -> {
            final List<String> steamIds = invocation.getArgument(0);
            final LocalDate start = invocation.getArgument(1);
            final LocalDate end = invocation.getArgument(2);
            final long spanDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            if (spanDays > 20) {
                return history;
            }
            final List<Guess> rows = new ArrayList<>();
            for (final String steamId : steamIds) {
                for (int i = 0; i < 35; i++) {
                    rows.add(guess(steamId, today, 2));
                }
            }
            return rows;
        }).when(guessRepository).findBySteamIdInAndGameDateBetween(anyList(), any(), any());
    }

    private GuessRepository.DailyTotalRow dailyTotal(String steamId, LocalDate date, long points) {
        final GuessRepository.DailyTotalRow row = mock(GuessRepository.DailyTotalRow.class);
        when(row.getSteamId()).thenReturn(steamId);
        when(row.getGameDate()).thenReturn(date);
        when(row.getTotalPoints()).thenReturn(points);
        return row;
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

        // Override the default (which would otherwise auto-grant "stale" 35 recent rounds
        // too): "stale" is genuinely dormant, so it's simply omitted here, leaving it with a
        // recent-rounds count of 0.
        final List<Guess> recentRounds = new ArrayList<>();
        for (int i = 0; i < 35; i++) recentRounds.add(guess("active", today, 2));
        doReturn(recentRounds).when(guessRepository).findBySteamIdInAndGameDateBetween(anyList(), any(), any());

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("active", captor.getValue().getSteamId());
    }

    @Test
    void excludesPlayerWithEnoughAllTimeRoundsButNotEnoughRecentRounds() {
        // Reproduces the reported production bug: a player with 200 all-time rounds who went
        // quiet, then played a single light day of just 5 rounds "today" — enough to have
        // satisfied the OLD "played at least once in the last 14 days" check, but far short of
        // the new 35-rounds-in-14-days activity floor, so this dormant player must be excluded.
        final GuessRepository.AllTimeStatsRow dormantVeteran = allTimeRow("dormantVeteran", 200, 2.0);
        final GuessRepository.AllTimeStatsRow active = allTimeRow("active", 100, 2.0);
        stubAllTimeStats(dormantVeteran, active);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("dormantVeteran", today, 1)); // played today, but just a fluke
        allDates.addAll(consecutiveDaysEnding("active", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        final List<Guess> recentRounds = new ArrayList<>();
        for (int i = 0; i < 5; i++) recentRounds.add(guess("dormantVeteran", today, 2)); // only 5 in the window
        for (int i = 0; i < 35; i++) recentRounds.add(guess("active", today, 2));
        doReturn(recentRounds).when(guessRepository).findBySteamIdInAndGameDateBetween(anyList(), any(), any());

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
    void doesNothingWhenNoOneHasEverPlayed() {
        // Not even the last-resort relaxation in findEligibleCandidates() can invent a
        // candidate when literally nobody has ever recorded a round.
        stubAllTimeStats();

        service.computeAndPersistForToday();

        verify(playerSpotlightRepository, never()).save(any());
    }

    @Test
    void fallsBackToLastResortCandidateWhenNoOneMeetsTheEstablishedBar() {
        // Nobody clears the normal 70-rounds/35-in-14-days bar, but someone has played at
        // least once — the box should still appear via the last-resort relaxation rather than
        // disappearing for the day.
        final GuessRepository.AllTimeStatsRow tooFewRounds = allTimeRow("tooFewRounds", 10, 2.0);
        stubAllTimeStats(tooFewRounds);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("tooFewRounds", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("tooFewRounds", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.MILESTONE, captor.getValue().getInsightType());
    }

    @Test
    void playerCooldownExcludesRecentlyFeaturedPlayerWhenAlternativesExist() {
        final GuessRepository.AllTimeStatsRow recentlyFeatured = allTimeRow("recentlyFeatured", 100, 2.0);
        final GuessRepository.AllTimeStatsRow freshPlayer = allTimeRow("freshPlayer", 100, 2.0);
        stubAllTimeStats(recentlyFeatured, freshPlayer);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("recentlyFeatured", today, 1));
        allDates.addAll(consecutiveDaysEnding("freshPlayer", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        // Both candidates only qualify for the MILESTONE fallback (single date, no other
        // tier-qualifying signal), so once the cooldown drops "recentlyFeatured", "freshPlayer"
        // is the only remaining candidate — no need to replicate the RNG.
        final PlayerSpotlight yesterdaySpotlight = new PlayerSpotlight();
        yesterdaySpotlight.setGameDate(today.minusDays(1));
        yesterdaySpotlight.setSteamId("recentlyFeatured");
        yesterdaySpotlight.setInsightType(PlayerSpotlightInsightType.MILESTONE);
        yesterdaySpotlight.setHeadline("A steady presence!");
        yesterdaySpotlight.setDetail("Has played 99 rounds and counting, averaging 2.0 pts/round.");
        when(playerSpotlightRepository.findByGameDateBetween(any(), any())).thenReturn(List.of(yesterdaySpotlight));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("freshPlayer", captor.getValue().getSteamId());
    }

    @Test
    void playerCooldownIsIgnoredWhenExcludingTheOnlyEligiblePlayerWouldLeaveNoSpotlight() {
        final GuessRepository.AllTimeStatsRow onlyPlayer = allTimeRow("onlyPlayer", 100, 2.0);
        stubAllTimeStats(onlyPlayer);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("onlyPlayer", today, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        // "onlyPlayer" is the only eligible candidate today, but was also featured yesterday.
        // The player cooldown must not suppress the entire pool just to enforce variety.
        final PlayerSpotlight yesterdaySpotlight = new PlayerSpotlight();
        yesterdaySpotlight.setGameDate(today.minusDays(1));
        yesterdaySpotlight.setSteamId("onlyPlayer");
        yesterdaySpotlight.setInsightType(PlayerSpotlightInsightType.MILESTONE);
        yesterdaySpotlight.setHeadline("A steady presence!");
        yesterdaySpotlight.setDetail("Has played 99 rounds and counting, averaging 2.0 pts/round.");
        when(playerSpotlightRepository.findByGameDateBetween(any(), any())).thenReturn(List.of(yesterdaySpotlight));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("onlyPlayer", captor.getValue().getSteamId());
    }

    @Test
    void skipsRecomputeWhenAlreadyPersistedForToday() {
        when(playerSpotlightRepository.existsById(today)).thenReturn(true);

        service.computeAndPersistForToday();

        verify(guessRepository, never()).aggregateAllTimeStatsHavingMinRounds(anyLong());
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
        // documented tie-break: sort by steamId, then a Random seeded by the mixed epoch day.
        final List<String> sorted = List.of("playerA", "playerB").stream().sorted().toList();
        final int expectedIndex = new Random(PlayerSpotlightService.mixSeed(today.toEpochDay())).nextInt(sorted.size());
        assertEquals(sorted.get(expectedIndex), picked);
    }

    @Test
    void topCommentTierWinsWhenEligibleAuthorHadYesterdaysMostReactedComment() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow commenter = allTimeRow("commenter", 100, 2.0);
        stubAllTimeStats(commenter);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("commenter", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final Comment comment = new Comment();
        comment.setId(77L);
        comment.setSteamId("commenter");
        comment.setGameDate(yesterday);
        comment.setBody("These picks were wild");
        comment.setArchived(false);
        comment.setCreatedAt(OffsetDateTime.parse("2026-07-30T18:00:00Z"));
        when(commentRepository.findTopReactedCommentId(yesterday)).thenReturn(Optional.of(77L));
        when(commentRepository.findById(77L)).thenReturn(Optional.of(comment));

        final CommentReactionRepository.ReactionCountRow countRow =
                mock(CommentReactionRepository.ReactionCountRow.class);
        when(countRow.getCommentId()).thenReturn(77L);
        when(countRow.getReactionType()).thenReturn(ReactionType.THUMBS_UP);
        when(countRow.getReactionCount()).thenReturn(5L);
        when(commentReactionRepository.countByCommentIds(List.of(77L))).thenReturn(List.of(countRow));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("commenter", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.TOP_COMMENT, captor.getValue().getInsightType());
        assertTrue(captor.getValue().getDetail().contains("These picks were wild"));
        assertEquals(5.0, captor.getValue().getStatValue());
    }

    @Test
    void bestDayEverTierWinsWhenYesterdayIsANewPersonalRecord() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow recordBreaker = allTimeRow("recordBreaker", 100, 2.0);
        stubAllTimeStats(recordBreaker);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("recordBreaker", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final List<GuessRepository.DailyTotalRow> dailyTotals = List.of(
                dailyTotal("recordBreaker", yesterday.minusDays(10), 8),
                dailyTotal("recordBreaker", yesterday.minusDays(5), 12),
                dailyTotal("recordBreaker", yesterday, 24)
        );
        when(guessRepository.findDailyTotalsBySteamIdIn(anyList())).thenReturn(dailyTotals);

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

        stubRecentHistory(List.of(
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
        stubRecentHistory(history);

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
        stubRecentHistory(history);

        // Neither candidate spuriously qualifies for the other's tier or for
        // BEST_DAY_EVER / BEAT_THE_ODDS / WELCOME_BACK / HOT_STREAK:
        // - "streaker" has no entries in the stubbed history prefetch (only
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
        // then one is drawn via new Random(mixSeed(today.toEpochDay())).nextInt(qualifying.size()).
        final List<PlayerSpotlightInsightType> qualifyingInOrder =
                List.of(PlayerSpotlightInsightType.DAY_STREAK, PlayerSpotlightInsightType.MOST_IMPROVED);
        final int expectedIndex = new Random(PlayerSpotlightService.mixSeed(today.toEpochDay())).nextInt(qualifyingInOrder.size());
        final PlayerSpotlightInsightType expectedType = qualifyingInOrder.get(expectedIndex);
        final String expectedSteamId =
                expectedType == PlayerSpotlightInsightType.DAY_STREAK ? "streaker" : "improver";

        assertEquals(expectedType, captor.getValue().getInsightType());
        assertEquals(expectedSteamId, captor.getValue().getSteamId());
    }

    @Test
    void cooldownExcludesRecentlyFeaturedTierFromLottery() {
        // Same two-candidate, two-tier setup as lotteryPicksAmongMultipleQualifyingCompetitiveTiers
        // ("streaker" qualifies for DAY_STREAK, "improver" for MOST_IMPROVED), but DAY_STREAK was
        // already featured yesterday. The cooldown should drop it from today's pool, leaving
        // MOST_IMPROVED as the only (and thus deterministic) winner — no need to replicate the RNG.
        final GuessRepository.AllTimeStatsRow streaker = allTimeRow("streaker", 100, 2.0);
        final GuessRepository.AllTimeStatsRow improver = allTimeRow("improver", 100, 2.0);
        stubAllTimeStats(streaker, improver);

        final List<GuessRepository.UserDateRow> allDates = new ArrayList<>();
        allDates.addAll(consecutiveDaysEnding("streaker", today, 6));
        allDates.addAll(consecutiveDaysEnding("improver", today, 1));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(allDates);

        final LocalDate last30Start = today.minusDays(30);
        final LocalDate prior30Start = today.minusDays(60);
        final List<Guess> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) history.add(guess("improver", last30Start.plusDays(i), 4));
        for (int i = 0; i < 15; i++) history.add(guess("improver", prior30Start.plusDays(i), 2));
        stubRecentHistory(history);

        final PlayerSpotlight recentDayStreak = new PlayerSpotlight();
        recentDayStreak.setGameDate(today.minusDays(1));
        recentDayStreak.setSteamId("someoneElse");
        recentDayStreak.setInsightType(PlayerSpotlightInsightType.DAY_STREAK);
        recentDayStreak.setHeadline("On a hot streak!");
        recentDayStreak.setDetail("On a 6-day streak of playing every day.");
        when(playerSpotlightRepository.findByGameDateBetween(any(), any())).thenReturn(List.of(recentDayStreak));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals(PlayerSpotlightInsightType.MOST_IMPROVED, captor.getValue().getInsightType());
        assertEquals("improver", captor.getValue().getSteamId());
    }

    @Test
    void cooldownIsIgnoredWhenExcludingTheOnlyQualifyingTierWouldLeaveNoSpotlight() {
        final GuessRepository.AllTimeStatsRow streaker = allTimeRow("streaker", 100, 2.0);
        stubAllTimeStats(streaker);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("streaker", today, 6);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        // DAY_STREAK is the only qualifying tier today, but it was also featured yesterday.
        // The cooldown must not suppress the entire competitive pool just to enforce variety.
        final PlayerSpotlight recentDayStreak = new PlayerSpotlight();
        recentDayStreak.setGameDate(today.minusDays(1));
        recentDayStreak.setSteamId("streaker");
        recentDayStreak.setInsightType(PlayerSpotlightInsightType.DAY_STREAK);
        recentDayStreak.setHeadline("On a hot streak!");
        recentDayStreak.setDetail("On a 5-day streak of playing every day.");
        when(playerSpotlightRepository.findByGameDateBetween(any(), any())).thenReturn(List.of(recentDayStreak));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals(PlayerSpotlightInsightType.DAY_STREAK, captor.getValue().getInsightType());
        assertEquals("streaker", captor.getValue().getSteamId());
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
        final List<String> possibleDetails = PlayerSpotlightService.MILESTONE_CLOSING_IN_DETAILS.stream()
                .map(template -> String.format(template, 100L, "rounds", 2L))
                .toList();
        assertTrue(possibleDetails.contains(captor.getValue().getDetail()));
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
        final List<String> possibleDetails = PlayerSpotlightService.MILESTONE_CLOSING_IN_DETAILS.stream()
                .map(template -> String.format(template, 1000L, "lifetime points", 5L))
                .toList();
        assertTrue(possibleDetails.contains(captor.getValue().getDetail()));
    }
}
