package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.steam5.config.SeasonProperties;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonAwardCategory;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.domain.SeasonStatus;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.SeasonAwardResultRepository;
import org.steam5.repository.SeasonRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeasonServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonAwardResultRepository awardResultRepository;
    private GuessRepository guessRepository;
    private SeasonCreatorService seasonCreator;
    private SeasonProperties seasonProperties;
    private SeasonService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        awardResultRepository = mock(SeasonAwardResultRepository.class);
        guessRepository = mock(GuessRepository.class);
        seasonCreator = mock(SeasonCreatorService.class);
        seasonProperties = new SeasonProperties();
        service = new SeasonService(seasonRepository, awardResultRepository, guessRepository,
                seasonProperties, seasonCreator);
    }

    private static Season seasonWith(int number, LocalDate start, LocalDate end, SeasonStatus status) {
        Season s = new Season();
        s.setId((long) number);
        s.setSeasonNumber(number);
        s.setStartDate(start);
        s.setEndDate(end);
        s.setStatus(status);
        return s;
    }

    private static GuessRepository.SeasonStatRow statRow(String steamId, long totalPoints, long hits,
                                                          long flops, long rounds, long activeDays) {
        GuessRepository.SeasonStatRow row = mock(GuessRepository.SeasonStatRow.class);
        when(row.getSteamId()).thenReturn(steamId);
        when(row.getTotalPoints()).thenReturn(totalPoints);
        when(row.getHits()).thenReturn(hits);
        when(row.getFlops()).thenReturn(flops);
        when(row.getRounds()).thenReturn(rounds);
        when(row.getActiveDays()).thenReturn(activeDays);
        return row;
    }

    // ensureSeasonForDate ---------------------------------------------------------------

    @Test
    void ensureSeasonForDate_returnsExistingSeasonWhenFound() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season existing = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.of(existing));

        Season result = service.ensureSeasonForDate(date);

        assertSame(existing, result);
        verify(seasonCreator, never()).createSeasonsUntil(any());
    }

    @Test
    void ensureSeasonForDate_callsCreatorWhenNoSeasonFound() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season created = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty());
        when(seasonCreator.createSeasonsUntil(date)).thenReturn(created);

        Season result = service.ensureSeasonForDate(date);

        assertSame(created, result);
        verify(seasonCreator, times(1)).createSeasonsUntil(date);
    }

    @Test
    void ensureSeasonForDate_retriesOnDataIntegrityViolation() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Season recovered = seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recovered));
        when(seasonCreator.createSeasonsUntil(date))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        Season result = service.ensureSeasonForDate(date);

        assertSame(recovered, result);
        verify(seasonRepository, times(2))
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    @Test
    void ensureSeasonForDate_throwsWhenSecondFindAlsoFails() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.empty());
        when(seasonCreator.createSeasonsUntil(date))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        assertThrows(IllegalStateException.class, () -> service.ensureSeasonForDate(date));
    }

    @Test
    void ensureSeasonForDate_throwsOnNullDate() {
        assertThrows(NullPointerException.class, () -> service.ensureSeasonForDate(null));
    }

    // findSeasonContaining --------------------------------------------------------------

    @Test
    void findSeasonContaining_delegatesToRepository() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        Season s = seasonWith(2, date.minusDays(3), date.plusDays(3), SeasonStatus.ACTIVE);
        when(seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date))
                .thenReturn(Optional.of(s));

        Optional<Season> result = service.findSeasonContaining(date);

        assertTrue(result.isPresent());
        assertSame(s, result.get());
        verify(seasonRepository, times(1))
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    // listSeasonsDescending -------------------------------------------------------------

    @Test
    void listSeasonsDescending_returnsRepositoryResult() {
        List<Season> seasons = List.of(
                seasonWith(2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), SeasonStatus.ACTIVE),
                seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), SeasonStatus.FINALIZED)
        );
        when(seasonRepository.findAllByOrderBySeasonNumberDesc()).thenReturn(seasons);

        List<Season> result = service.listSeasonsDescending();

        assertEquals(seasons, result);
        verify(seasonRepository, times(1)).findAllByOrderBySeasonNumberDesc();
    }

    // buildSeasonReport -----------------------------------------------------------------

    @Test
    void buildSeasonReport_returnsEmptyStatsForFutureSeasonWithNoData() {
        LocalDate today = LocalDate.now();
        Season future = seasonWith(99, today.plusDays(10), today.plusDays(40), SeasonStatus.ACTIVE);

        SeasonService.SeasonReport report = service.buildSeasonReport(future);

        assertNotNull(report);
        assertTrue(report.players().isEmpty());
        assertNotNull(report.summary());
        assertEquals(0, report.summary().totalPlayers());
        assertNull(report.summary().dataThrough());
        verify(guessRepository, never()).findSeasonStats(any(), any());
    }

    @Test
    void buildSeasonReport_throwsOnNullSeason() {
        assertThrows(NullPointerException.class, () -> service.buildSeasonReport(null));
    }

    // backfillHistoricalSeasons ---------------------------------------------------------

    @Test
    void backfillHistoricalSeasons_returnsEmptyListWhenNoGuesses() {
        when(guessRepository.findEarliestGameDate()).thenReturn(Optional.empty());

        List<Season> result = service.backfillHistoricalSeasons();

        assertTrue(result.isEmpty());
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void backfillHistoricalSeasons_delegatesRangeFromEarliestToLatestGuess() {
        LocalDate onlyDate = LocalDate.of(2026, 1, 1);
        when(guessRepository.findEarliestGameDate()).thenReturn(Optional.of(onlyDate));
        when(guessRepository.findLatestGameDate()).thenReturn(Optional.of(onlyDate));
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.empty());
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Season> result = service.backfillHistoricalSeasons();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getSeasonNumber());
        assertEquals(onlyDate, result.get(0).getStartDate());
        verify(guessRepository, times(1)).findLatestGameDate();
    }

    // findSeason --------------------------------------------------------------------------

    @Test
    void findSeason_delegatesToRepository() {
        Season s = seasonWith(4, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), SeasonStatus.FINALIZED);
        when(seasonRepository.findById(4L)).thenReturn(Optional.of(s));

        Optional<Season> result = service.findSeason(4L);

        assertTrue(result.isPresent());
        assertSame(s, result.get());
        verify(seasonRepository, times(1)).findById(4L);
    }

    @Test
    void findSeason_returnsEmptyWhenNotFound() {
        when(seasonRepository.findById(404L)).thenReturn(Optional.empty());

        Optional<Season> result = service.findSeason(404L);

        assertTrue(result.isEmpty());
    }

    // findSeasonByNumber --------------------------------------------------------------------

    @Test
    void findSeasonByNumber_delegatesToRepository() {
        Season s = seasonWith(7, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30), SeasonStatus.ACTIVE);
        when(seasonRepository.findBySeasonNumber(7)).thenReturn(Optional.of(s));

        Optional<Season> result = service.findSeasonByNumber(7);

        assertTrue(result.isPresent());
        assertSame(s, result.get());
        verify(seasonRepository, times(1)).findBySeasonNumber(7);
    }

    @Test
    void findSeasonByNumber_returnsEmptyWhenNotFound() {
        when(seasonRepository.findBySeasonNumber(999)).thenReturn(Optional.empty());

        Optional<Season> result = service.findSeasonByNumber(999);

        assertTrue(result.isEmpty());
    }

    // findLatestSeason ----------------------------------------------------------------------

    @Test
    void findLatestSeason_delegatesToRepository() {
        Season s = seasonWith(9, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), SeasonStatus.PLANNED);
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.of(s));

        Optional<Season> result = service.findLatestSeason();

        assertTrue(result.isPresent());
        assertSame(s, result.get());
        verify(seasonRepository, times(1)).findTopByOrderBySeasonNumberDesc();
    }

    @Test
    void findLatestSeason_returnsEmptyWhenNoSeasonsExist() {
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.empty());

        Optional<Season> result = service.findLatestSeason();

        assertTrue(result.isEmpty());
    }

    // findActiveSeasons ---------------------------------------------------------------------

    @Test
    void findActiveSeasons_delegatesToRepository() {
        List<Season> active = List.of(
                seasonWith(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE),
                seasonWith(2, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1), SeasonStatus.ACTIVE)
        );
        when(seasonRepository.findAllByStatusOrderBySeasonNumberAsc(SeasonStatus.ACTIVE)).thenReturn(active);

        List<Season> result = service.findActiveSeasons();

        assertEquals(active, result);
        verify(seasonRepository, times(1)).findAllByStatusOrderBySeasonNumberAsc(SeasonStatus.ACTIVE);
    }

    @Test
    void findActiveSeasons_returnsEmptyListWhenNoneActive() {
        when(seasonRepository.findAllByStatusOrderBySeasonNumberAsc(SeasonStatus.ACTIVE)).thenReturn(List.of());

        List<Season> result = service.findActiveSeasons();

        assertTrue(result.isEmpty());
    }

    // listAwardsForSeason / listAwardsForPlayer ----------------------------------------------

    @Test
    void listAwardsForSeason_delegatesToRepository() {
        SeasonAwardResult award = mock(SeasonAwardResult.class);
        when(awardResultRepository.findAllBySeasonIdOrderByCategoryAscPlacementLevelAsc(5L))
                .thenReturn(List.of(award));

        List<SeasonAwardResult> result = service.listAwardsForSeason(5L);

        assertEquals(List.of(award), result);
        verify(awardResultRepository, times(1)).findAllBySeasonIdOrderByCategoryAscPlacementLevelAsc(5L);
    }

    @Test
    void listAwardsForSeason_returnsEmptyListWhenNoAwards() {
        when(awardResultRepository.findAllBySeasonIdOrderByCategoryAscPlacementLevelAsc(6L))
                .thenReturn(List.of());

        List<SeasonAwardResult> result = service.listAwardsForSeason(6L);

        assertTrue(result.isEmpty());
    }

    @Test
    void listAwardsForPlayer_delegatesToRepository() {
        SeasonAwardResult award = mock(SeasonAwardResult.class);
        when(awardResultRepository.findAllBySteamIdOrderBySeasonSeasonNumberDescPlacementLevelAsc("steam123"))
                .thenReturn(List.of(award));

        List<SeasonAwardResult> result = service.listAwardsForPlayer("steam123");

        assertEquals(List.of(award), result);
        verify(awardResultRepository, times(1))
                .findAllBySteamIdOrderBySeasonSeasonNumberDescPlacementLevelAsc("steam123");
    }

    @Test
    void listAwardsForPlayer_returnsEmptyListWhenNoAwards() {
        when(awardResultRepository.findAllBySteamIdOrderBySeasonSeasonNumberDescPlacementLevelAsc("unknownSteamId"))
                .thenReturn(List.of());

        List<SeasonAwardResult> result = service.listAwardsForPlayer("unknownSteamId");

        assertTrue(result.isEmpty());
    }

    // buildSeasonReport (stats aggregation path) ---------------------------------------------

    @Test
    void buildSeasonReport_computesSummaryAndSortsPlayersByPointsDescending() {
        // Season is fully in the past, so candidateEnd resolves to its fixed endDate rather
        // than today's date — keeps the mocked repository call arguments deterministic
        // regardless of the test machine's timezone vs. the service's UTC "today".
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 30);
        Season completed = seasonWith(1, start, end, SeasonStatus.FINALIZED);

        GuessRepository.SeasonStatRow rowA = statRow("playerA", 50L, 5L, 1L, 10L, 8L);
        GuessRepository.SeasonStatRow rowB = statRow("playerB", 80L, 6L, 0L, 12L, 10L);

        when(guessRepository.findSeasonStats(start, end)).thenReturn(List.of(rowA, rowB));
        when(guessRepository.findSeasonDates(start, end)).thenReturn(List.of());

        SeasonService.SeasonReport report = service.buildSeasonReport(completed);

        assertEquals(2, report.players().size());
        assertEquals("playerB", report.players().get(0).steamId());
        assertEquals(80L, report.players().get(0).totalPoints());
        assertEquals("playerA", report.players().get(1).steamId());

        SeasonService.SeasonSummary summary = report.summary();
        assertEquals(2, summary.totalPlayers());
        assertEquals(22L, summary.totalRounds());
        assertEquals(130L, summary.totalPoints());
        assertEquals(11L, summary.totalHits());
        assertEquals(9.0d, summary.averageActiveDays());
        assertEquals(end, summary.dataThrough());
    }

    // buildSeasonHighlights -------------------------------------------------------------------

    @Test
    void buildSeasonHighlights_returnsAllNullsWhenSeasonHasNotStarted() {
        LocalDate today = LocalDate.now();
        Season future = seasonWith(10, today.plusDays(5), today.plusDays(20), SeasonStatus.PLANNED);

        SeasonService.SeasonDailyHighlights highlights = service.buildSeasonHighlights(future);

        assertNull(highlights.highestAvg());
        assertNull(highlights.lowestAvg());
        assertNull(highlights.busiest());
        assertNull(highlights.easiestRound());
        assertNull(highlights.hardestRound());
        verify(guessRepository, never()).findDailyAvgScoresInRange(any(), any());
    }

    @Test
    void buildSeasonHighlights_picksHighestLowestBusiestDayAndEasiestHardestRound() {
        // Fully-past season: candidateEnd resolves to the fixed endDate, keeping the mocked
        // repository call arguments deterministic regardless of the machine's timezone.
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 10);
        Season completed = seasonWith(11, start, end, SeasonStatus.FINALIZED);

        GuessRepository.DailyAvgScoreRow day1 = mock(GuessRepository.DailyAvgScoreRow.class);
        when(day1.getGameDate()).thenReturn(start);
        when(day1.getAvgScore()).thenReturn(3.0d);
        when(day1.getPlayerCount()).thenReturn(10L);

        GuessRepository.DailyAvgScoreRow day2 = mock(GuessRepository.DailyAvgScoreRow.class);
        when(day2.getGameDate()).thenReturn(start.plusDays(1));
        when(day2.getAvgScore()).thenReturn(5.0d);
        when(day2.getPlayerCount()).thenReturn(3L);

        GuessRepository.DailyAvgScoreRow day3 = mock(GuessRepository.DailyAvgScoreRow.class);
        when(day3.getGameDate()).thenReturn(start.plusDays(2));
        when(day3.getAvgScore()).thenReturn(1.0d);
        when(day3.getPlayerCount()).thenReturn(20L);

        when(guessRepository.findDailyAvgScoresInRange(start, end)).thenReturn(List.of(day1, day2, day3));

        GuessRepository.RoundAvgScoreRow round1 = mock(GuessRepository.RoundAvgScoreRow.class);
        when(round1.getGameDate()).thenReturn(start);
        when(round1.getRoundIndex()).thenReturn(1);
        when(round1.getAppId()).thenReturn(100L);
        when(round1.getAppName()).thenReturn("Game A");
        when(round1.getAvgScore()).thenReturn(4.0d);
        when(round1.getPlayerCount()).thenReturn(5L);

        GuessRepository.RoundAvgScoreRow round2 = mock(GuessRepository.RoundAvgScoreRow.class);
        when(round2.getGameDate()).thenReturn(start.plusDays(1));
        when(round2.getRoundIndex()).thenReturn(2);
        when(round2.getAppId()).thenReturn(101L);
        when(round2.getAppName()).thenReturn("Game B");
        when(round2.getAvgScore()).thenReturn(4.0d);
        when(round2.getPlayerCount()).thenReturn(8L);

        GuessRepository.RoundAvgScoreRow round3 = mock(GuessRepository.RoundAvgScoreRow.class);
        when(round3.getGameDate()).thenReturn(start.plusDays(2));
        when(round3.getRoundIndex()).thenReturn(3);
        when(round3.getAppId()).thenReturn(102L);
        when(round3.getAppName()).thenReturn("Game C");
        when(round3.getAvgScore()).thenReturn(1.0d);
        when(round3.getPlayerCount()).thenReturn(50L);

        when(guessRepository.findRoundAvgScoresInRange(start, end)).thenReturn(List.of(round1, round2, round3));

        SeasonService.SeasonDailyHighlights highlights = service.buildSeasonHighlights(completed);

        assertEquals(start.plusDays(1), highlights.highestAvg().date());
        assertEquals(5.0d, highlights.highestAvg().avgScore());
        assertEquals(start.plusDays(2), highlights.lowestAvg().date());
        assertEquals(1.0d, highlights.lowestAvg().avgScore());
        assertEquals(start.plusDays(2), highlights.busiest().date());
        assertEquals(20L, highlights.busiest().playerCount());

        // easiest round: highest avgScore, ties broken by higher playerCount (round2 beats round1)
        assertEquals("Game B", highlights.easiestRound().appName());
        assertEquals(8L, highlights.easiestRound().playerCount());
        // hardest round: lowest avgScore
        assertEquals("Game C", highlights.hardestRound().appName());
    }

    @Test
    void buildSeasonHighlights_busiestSkipsNullAvgScoreRows() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 5);
        Season completed = seasonWith(12, start, end, SeasonStatus.FINALIZED);

        GuessRepository.DailyAvgScoreRow busyButUnscored = mock(GuessRepository.DailyAvgScoreRow.class);
        when(busyButUnscored.getGameDate()).thenReturn(start);
        when(busyButUnscored.getAvgScore()).thenReturn(null);
        when(busyButUnscored.getPlayerCount()).thenReturn(100L);

        GuessRepository.DailyAvgScoreRow scored = mock(GuessRepository.DailyAvgScoreRow.class);
        when(scored.getGameDate()).thenReturn(start.plusDays(1));
        when(scored.getAvgScore()).thenReturn(2.0d);
        when(scored.getPlayerCount()).thenReturn(1L);

        when(guessRepository.findDailyAvgScoresInRange(start, end)).thenReturn(List.of(busyButUnscored, scored));
        when(guessRepository.findRoundAvgScoresInRange(start, end)).thenReturn(List.of());

        SeasonService.SeasonDailyHighlights highlights = service.buildSeasonHighlights(completed);

        // the null-avgScore row is excluded from highest/lowest and busiest; busiest falls back to the
        // next-highest playerCount row that has a non-null avgScore.
        assertEquals(start.plusDays(1), highlights.highestAvg().date());
        assertEquals(start.plusDays(1), highlights.lowestAvg().date());
        assertEquals(start.plusDays(1), highlights.busiest().date());
        assertEquals(1L, highlights.busiest().playerCount());
        assertEquals(2.0d, highlights.busiest().avgScore());
    }

    // finalizeSeason ----------------------------------------------------------------------------

    @Test
    void finalizeSeason_generatesAwardsAndTransitionsStatusToFinalized() {
        Season season = seasonWith(20, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30), SeasonStatus.ACTIVE);
        season.setAwardSeed(42L);
        seasonProperties.getAwards().setCategories(List.of(SeasonAwardCategory.MOST_POINTS));
        seasonProperties.getAwards().setMinRounds(1);

        GuessRepository.SeasonStatRow rowA = statRow("playerA", 100L, 10L, 2L, 20L, 15L);
        GuessRepository.SeasonStatRow rowB = statRow("playerB", 50L, 5L, 1L, 18L, 10L);

        when(seasonRepository.findById(20L)).thenReturn(Optional.of(season));
        when(guessRepository.findSeasonStats(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of(rowA, rowB));
        when(guessRepository.findSeasonDates(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of());
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        Season result = service.finalizeSeason(season);

        assertEquals(SeasonStatus.FINALIZED, result.getStatus());
        assertNotNull(result.getAwardsFinalizedAt());
        verify(awardResultRepository, times(1)).deleteAllBySeasonId(20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SeasonAwardResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(awardResultRepository, times(1)).saveAll(captor.capture());
        List<SeasonAwardResult> saved = captor.getValue();

        assertEquals(2, saved.size());
        assertEquals("playerA", saved.get(0).getSteamId());
        assertEquals(1, saved.get(0).getPlacementLevel());
        assertEquals(100L, saved.get(0).getMetricValue());
        assertEquals("playerB", saved.get(1).getSteamId());
        assertEquals(2, saved.get(1).getPlacementLevel());
    }

    @Test
    void finalizeSeason_excludesPlayersBelowMinRounds() {
        Season season = seasonWith(21, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), SeasonStatus.ACTIVE);
        seasonProperties.getAwards().setCategories(List.of(SeasonAwardCategory.MOST_POINTS));
        // default minRounds is 15

        GuessRepository.SeasonStatRow qualifies = statRow("qualifies", 30L, 3L, 0L, 20L, 15L);
        GuessRepository.SeasonStatRow tooFewRounds = statRow("tooFewRounds", 999L, 5L, 0L, 5L, 5L);

        when(seasonRepository.findById(21L)).thenReturn(Optional.of(season));
        when(guessRepository.findSeasonStats(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of(qualifies, tooFewRounds));
        when(guessRepository.findSeasonDates(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of());
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        service.finalizeSeason(season);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SeasonAwardResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(awardResultRepository, times(1)).saveAll(captor.capture());
        List<SeasonAwardResult> saved = captor.getValue();

        assertEquals(1, saved.size());
        assertEquals("qualifies", saved.get(0).getSteamId());
    }

    @Test
    void finalizeSeason_producesNoAwardsWhenNoPlayerMeetsThreshold() {
        Season season = seasonWith(22, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30), SeasonStatus.ACTIVE);

        when(seasonRepository.findById(22L)).thenReturn(Optional.of(season));
        when(guessRepository.findSeasonStats(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of());
        when(guessRepository.findSeasonDates(season.getStartDate(), season.getEndDate()))
                .thenReturn(List.of());
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        Season result = service.finalizeSeason(season);

        assertEquals(SeasonStatus.FINALIZED, result.getStatus());
        verify(awardResultRepository, times(1)).saveAll(List.of());
    }

    // backfillRange -------------------------------------------------------------------------

    @Test
    void backfillRange_throwsOnNullStart() {
        assertThrows(IllegalArgumentException.class, () -> service.backfillRange(null, LocalDate.now()));
    }

    @Test
    void backfillRange_throwsOnNullEnd() {
        assertThrows(IllegalArgumentException.class, () -> service.backfillRange(LocalDate.now(), null));
    }

    @Test
    void backfillRange_throwsWhenStartAfterEnd() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 5);

        assertThrows(IllegalArgumentException.class, () -> service.backfillRange(start, end));
    }

    @Test
    void backfillRange_createsFirstSeasonWhenNoneExist() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.empty());
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Season> created = service.backfillRange(start, start);

        assertEquals(1, created.size());
        assertEquals(1, created.get(0).getSeasonNumber());
        assertEquals(start, created.get(0).getStartDate());
        assertEquals(start.plusDays(29), created.get(0).getEndDate());
        assertEquals(SeasonStatus.ACTIVE, created.get(0).getStatus());
    }

    @Test
    void backfillRange_createsSeasonsInGapAfterExisting() {
        Season existingLatest = seasonWith(3, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 10), SeasonStatus.FINALIZED);
        when(seasonRepository.findTopByOrderBySeasonNumberDesc()).thenReturn(Optional.of(existingLatest));
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Season> created = service.backfillRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15));

        assertEquals(2, created.size());
        assertEquals(4, created.get(0).getSeasonNumber());
        assertEquals(LocalDate.of(2026, 1, 11), created.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 2, 9), created.get(0).getEndDate());
        assertEquals(5, created.get(1).getSeasonNumber());
        assertEquals(LocalDate.of(2026, 2, 10), created.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 3, 11), created.get(1).getEndDate());
    }
}
