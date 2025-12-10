package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.config.SeasonProperties;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonAwardCategory;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.domain.SeasonStatus;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.SeasonAwardResultRepository;
import org.steam5.repository.SeasonRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final SeasonAwardResultRepository awardResultRepository;
    private final GuessRepository guessRepository;
    private final SeasonProperties seasonProperties;

    private static final int TIE_ROLL_MAX = 1_000_000;

    @Transactional(readOnly = true)
    public List<Season> listSeasonsDescending() {
        return seasonRepository.findAllByOrderBySeasonNumberDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Season> findSeason(Long id) {
        return seasonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Season> findSeasonByNumber(int seasonNumber) {
        return seasonRepository.findBySeasonNumber(seasonNumber);
    }

    @Transactional(readOnly = true)
    public Optional<Season> findLatestSeason() {
        return seasonRepository.findTopByOrderBySeasonNumberDesc();
    }

    @Transactional(readOnly = true)
    public List<Season> findActiveSeasons() {
        return seasonRepository.findAllByStatusOrderBySeasonNumberAsc(SeasonStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Optional<Season> findSeasonContaining(LocalDate date) {
        return seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "season-awards", key = "#seasonId")
    public List<SeasonAwardResult> listAwardsForSeason(Long seasonId) {
        return awardResultRepository.findAllBySeasonIdOrderByCategoryAscPlacementLevelAsc(seasonId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "player-awards", key = "#steamId")
    public List<SeasonAwardResult> listAwardsForPlayer(String steamId) {
        return awardResultRepository.findAllBySteamIdOrderBySeasonSeasonNumberDescPlacementLevelAsc(steamId);
    }

    @Transactional(readOnly = true)
    public SeasonReport buildSeasonReport(Season season) {
        Objects.requireNonNull(season, "season");
        LocalDate today = todayUtc();
        LocalDate candidateEnd = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        boolean hasStats = !candidateEnd.isBefore(season.getStartDate());
        List<PlayerSeasonStat> players = hasStats
                ? buildPlayerSeasonStats(season, candidateEnd)
                : List.of();
        LocalDate dataThrough = hasStats ? candidateEnd : null;
        SeasonSummary summary = summarizeSeason(season, dataThrough, players);
        return new SeasonReport(summary, players);
    }

    @Transactional(readOnly = true)
    public SeasonDailyHighlights buildSeasonHighlights(Season season) {
        Objects.requireNonNull(season, "season");
        LocalDate today = todayUtc();
        LocalDate candidateEnd = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        if (candidateEnd.isBefore(season.getStartDate())) {
            return new SeasonDailyHighlights(null, null, null, null, null);
        }
        List<GuessRepository.DailyAvgScoreRow> rows = guessRepository.findDailyAvgScoresInRange(season.getStartDate(), candidateEnd);
        List<GuessRepository.RoundAvgScoreRow> roundRows = guessRepository.findRoundAvgScoresInRange(season.getStartDate(), candidateEnd);

        GuessRepository.DailyAvgScoreRow highest = rows.stream()
                .filter(row -> row.getAvgScore() != null)
                .max(Comparator.comparing(GuessRepository.DailyAvgScoreRow::getAvgScore))
                .orElse(null);
        GuessRepository.DailyAvgScoreRow lowest = rows.stream()
                .filter(row -> row.getAvgScore() != null)
                .min(Comparator.comparing(GuessRepository.DailyAvgScoreRow::getAvgScore))
                .orElse(null);
        GuessRepository.DailyAvgScoreRow busiest = rows.stream()
                .filter(row -> row.getPlayerCount() != null)
                .max(Comparator.comparing(GuessRepository.DailyAvgScoreRow::getPlayerCount))
                .orElse(null);

        Comparator<GuessRepository.RoundAvgScoreRow> roundComparator = Comparator
                .comparing(GuessRepository.RoundAvgScoreRow::getAvgScore)
                .thenComparing(GuessRepository.RoundAvgScoreRow::getPlayerCount);

        GuessRepository.RoundAvgScoreRow easiestRound = roundRows.stream()
                .filter(row -> row.getAvgScore() != null)
                .max(roundComparator)
                .orElse(null);

        GuessRepository.RoundAvgScoreRow hardestRound = roundRows.stream()
                .filter(row -> row.getAvgScore() != null)
                .min(roundComparator)
                .orElse(null);

        return new SeasonDailyHighlights(
                toDailyHighlight(highest),
                toDailyHighlight(lowest),
                toDailyHighlight(busiest),
                toRoundHighlight(easiestRound),
                toRoundHighlight(hardestRound)
        );
    }

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response", "season-detail-response"}, allEntries = true)
    public Season ensureSeasonForDate(LocalDate date) {
        Objects.requireNonNull(date, "date");

        return seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
                .orElseGet(() -> createSeasonsUntil(date));
    }

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response", "season-detail-response"}, allEntries = true)
    public List<Season> backfillHistoricalSeasons() {
        final Optional<LocalDate> earliestOpt = guessRepository.findEarliestGameDate();
        if (earliestOpt.isEmpty()) {
            log.warn("No guesses found, skipping season backfill.");
            return List.of();
        }
        final LocalDate earliest = earliestOpt.get();
        final LocalDate latest = guessRepository.findLatestGameDate().orElse(earliest);
        return backfillRange(earliest, latest);
    }

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response", "season-detail-response"}, allEntries = true)
    public List<Season> backfillRange(LocalDate startInclusive, LocalDate endInclusive) {
        if (startInclusive == null || endInclusive == null) {
            throw new IllegalArgumentException("startDate and endDate must be provided");
        }
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }

        final List<Season> created = new ArrayList<>();
        Season last = seasonRepository.findTopByOrderBySeasonNumberDesc().orElse(null);
        int nextNumber;

        if (last == null) {
            last = seasonRepository.save(buildSeason(1, startInclusive, SeasonStatus.ACTIVE));
            created.add(last);
            nextNumber = 2;
        } else {
            nextNumber = last.getSeasonNumber() + 1;
        }

        LocalDate nextStart = last.getEndDate().plusDays(1);
        while (!nextStart.isAfter(endInclusive)) {
            last = seasonRepository.save(buildSeason(nextNumber++, nextStart, SeasonStatus.ACTIVE));
            created.add(last);
            nextStart = last.getEndDate().plusDays(1);
        }
        return created;
    }

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response", "season-awards-response", "season-awards", "player-awards", "season-detail-response"}, allEntries = true)
    public Season finalizeSeason(Season season) {
        Objects.requireNonNull(season, "season");
        Season managed = seasonRepository.findById(season.getId()).orElse(season);
        log.info("Finalizing season #{} ({} - {})", managed.getSeasonNumber(), managed.getStartDate(), managed.getEndDate());

        List<GuessRepository.SeasonStatRow> rows = guessRepository.findSeasonStats(managed.getStartDate(), managed.getEndDate());
        Map<String, List<LocalDate>> participationDates = buildSeasonParticipationDates(managed.getStartDate(), managed.getEndDate());
        Map<String, SeasonStats> statsByPlayer = rows.stream()
                .map(row -> buildSeasonStats(row, participationDates.getOrDefault(row.getSteamId(), List.of())))
                .collect(Collectors.toMap(SeasonStats::steamId, Function.identity()));
        final int minRoundsRequired = Math.max(1, seasonProperties.getAwards().getMinRounds());
        statsByPlayer.entrySet().removeIf(entry -> entry.getValue().rounds() < minRoundsRequired);

        awardResultRepository.deleteAllBySeasonId(managed.getId());

        List<SeasonAwardResult> newResults = new ArrayList<>();
        for (SeasonAwardCategory category : seasonProperties.getAwards().getCategories()) {
            newResults.addAll(generateAwardsForCategory(managed, category, statsByPlayer, minRoundsRequired));
        }
        awardResultRepository.saveAll(newResults);

        managed.setStatus(SeasonStatus.FINALIZED);
        managed.setAwardsFinalizedAt(OffsetDateTime.now());
        managed.setUpdatedAt(OffsetDateTime.now());
        return seasonRepository.save(managed);
    }

    private Season createSeasonsUntil(LocalDate date) {
        Season last = seasonRepository.findTopByOrderBySeasonNumberDesc().orElse(null);
        if (last == null) {
            LocalDate startDate = date;
            return seasonRepository.save(buildSeason(1, startDate, SeasonStatus.ACTIVE));
        }

        Season current = last;
        int nextNumber = last.getSeasonNumber() + 1;
        LocalDate nextStart = last.getEndDate().plusDays(1);
        while (current.getEndDate().isBefore(date)) {
            current = seasonRepository.save(buildSeason(nextNumber++, nextStart, SeasonStatus.ACTIVE));
            nextStart = current.getEndDate().plusDays(1);
        }
        return current;
    }

    private Season buildSeason(int seasonNumber, LocalDate startDate, SeasonStatus status) {
        Season season = new Season();
        season.setSeasonNumber(seasonNumber);
        season.setStartDate(startDate);
        season.setEndDate(startDate.plusDays(seasonLengthDays() - 1L));
        season.setStatus(status);
        season.setAwardSeed(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        season.setAwardsFinalizedAt(null);
        season.setCreatedAt(OffsetDateTime.now());
        season.setUpdatedAt(OffsetDateTime.now());
        return season;
    }

    private SeasonStats buildSeasonStats(GuessRepository.SeasonStatRow row, List<LocalDate> participationDates) {
        long totalPoints = coerce(row.getTotalPoints());
        long hits = coerce(row.getHits());
        long rounds = coerce(row.getRounds());
        long activeDays = row.getActiveDays() != null ? row.getActiveDays() : participationDates.size();
        double avgPointsPerDay = activeDays > 0 ? (double) totalPoints / activeDays : 0d;
        long longestStreak = calculateLongestStreak(participationDates);
        return new SeasonStats(row.getSteamId(), totalPoints, hits, rounds, activeDays, avgPointsPerDay, longestStreak);
    }

    private Map<String, List<LocalDate>> buildSeasonParticipationDates(LocalDate startDate, LocalDate endDate) {
        List<GuessRepository.SeasonDateRow> rows = guessRepository.findSeasonDates(startDate, endDate);
        Map<String, List<LocalDate>> map = new HashMap<>();
        for (GuessRepository.SeasonDateRow row : rows) {
            map.computeIfAbsent(row.getSteamId(), key -> new ArrayList<>());
            List<LocalDate> dates = map.get(row.getSteamId());
            LocalDate date = row.getGameDate();
            if (dates.isEmpty() || !dates.get(dates.size() - 1).equals(date)) {
                dates.add(date);
            }
        }
        return map;
    }

    private static long calculateLongestStreak(List<LocalDate> dates) {
        long best = 0;
        long current = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            if (previous == null) {
                current = 1;
            } else if (date.equals(previous.plusDays(1))) {
                current += 1;
            } else if (date.equals(previous)) {
                continue;
            } else {
                current = 1;
            }
            previous = date;
            if (current > best) {
                best = current;
            }
        }
        return best;
    }

    private List<SeasonAwardResult> generateAwardsForCategory(Season season,
                                                              SeasonAwardCategory category,
                                                              Map<String, SeasonStats> statsByPlayer,
                                                              int minRoundsRequired) {
        if (statsByPlayer.isEmpty()) {
            return List.of();
        }

        final int maxPlacements = Math.max(1, seasonProperties.getAwards().getMaxPlacements());
        Comparator<SeasonStats> comparator = Comparator
                .comparingLong((SeasonStats stats) -> metricValue(category, stats))
                .reversed()
                .thenComparingInt(stats -> tieBreakRoll(season, category, stats.steamId()));

        List<SeasonStats> ordered = statsByPlayer.values().stream()
                .filter(stats -> stats.rounds() >= minRoundsRequired)
                .filter(stats -> metricValue(category, stats) > 0)
                .sorted(comparator)
                .limit(maxPlacements)
                .toList();

        List<SeasonAwardResult> results = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            SeasonStats stats = ordered.get(i);
            results.add(buildAward(season, category, stats, i + 1));
        }
        return results;
    }

    private SeasonAwardResult buildAward(Season season,
                                         SeasonAwardCategory category,
                                         SeasonStats stats,
                                         int placementLevel) {
        long metricValue = metricValue(category, stats);
        int roll = tieBreakRoll(season, category, stats.steamId());

        SeasonAwardResult result = new SeasonAwardResult();
        result.setSeason(season);
        result.setCategory(category);
        result.setPlacementLevel(placementLevel);
        result.setSteamId(stats.steamId());
        result.setMetricValue(metricValue);
        result.setTiebreakRoll(roll);
        result.setCreatedAt(OffsetDateTime.now());
        return result;
    }

    private long metricValue(SeasonAwardCategory category, SeasonStats stats) {
        return switch (category) {
            case MOST_POINTS -> stats.totalPoints();
            case MOST_HITS -> stats.hits();
            case HIGHEST_AVG_POINTS_PER_DAY -> Math.round(stats.avgPointsPerDay() * 100.0d);
            case LONGEST_STREAK -> stats.longestStreak();
        };
    }

    private List<PlayerSeasonStat> buildPlayerSeasonStats(Season season, LocalDate statsEnd) {
        List<GuessRepository.SeasonStatRow> rows = guessRepository.findSeasonStats(season.getStartDate(), statsEnd);
        Map<String, List<LocalDate>> participationDates = buildSeasonParticipationDates(season.getStartDate(), statsEnd);
        return rows.stream()
                .map(row -> buildSeasonStats(row, participationDates.getOrDefault(row.getSteamId(), List.of())))
                .map(this::mapPlayerSeasonStat)
                .sorted(Comparator.comparingLong(PlayerSeasonStat::totalPoints)
                        .reversed()
                        .thenComparing(PlayerSeasonStat::steamId))
                .toList();
    }

    private PlayerSeasonStat mapPlayerSeasonStat(SeasonStats stats) {
        double avgPointsPerRound = stats.rounds() > 0
                ? (double) stats.totalPoints() / stats.rounds()
                : 0d;
        return new PlayerSeasonStat(
                stats.steamId(),
                stats.totalPoints(),
                stats.hits(),
                stats.rounds(),
                stats.activeDays(),
                stats.avgPointsPerDay(),
                avgPointsPerRound,
                stats.longestStreak()
        );
    }

    private SeasonSummary summarizeSeason(Season season,
                                          LocalDate dataThrough,
                                          List<PlayerSeasonStat> players) {
        final int durationDays = (int) Math.max(0, ChronoUnit.DAYS.between(season.getStartDate(), season.getEndDate()) + 1);
        int completedDays = 0;
        if (dataThrough != null) {
            completedDays = (int) (ChronoUnit.DAYS.between(season.getStartDate(), dataThrough) + 1);
            if (completedDays < 0) {
                completedDays = 0;
            } else if (completedDays > durationDays) {
                completedDays = durationDays;
            }
        }

        long totalPlayers = players.size();
        long totalRounds = players.stream().mapToLong(PlayerSeasonStat::rounds).sum();
        long totalPoints = players.stream().mapToLong(PlayerSeasonStat::totalPoints).sum();
        long totalHits = players.stream().mapToLong(PlayerSeasonStat::hits).sum();
        double avgPointsPerRound = totalRounds > 0 ? (double) totalPoints / totalRounds : 0d;
        double avgPointsPerPlayer = totalPlayers > 0 ? (double) totalPoints / totalPlayers : 0d;
        double hitRate = totalRounds > 0 ? (double) totalHits / totalRounds : 0d;
        double avgActiveDays = totalPlayers > 0
                ? players.stream().mapToLong(PlayerSeasonStat::activeDays).average().orElse(0d)
                : 0d;
        long longestStreak = players.stream().mapToLong(PlayerSeasonStat::longestStreak).max().orElse(0L);

        return new SeasonSummary(
                totalPlayers,
                totalRounds,
                totalPoints,
                totalHits,
                avgPointsPerRound,
                avgPointsPerPlayer,
                hitRate,
                avgActiveDays,
                longestStreak,
                durationDays,
                completedDays,
                dataThrough
        );
    }

    private static SeasonDailyHighlights.DailyHighlight toDailyHighlight(GuessRepository.DailyAvgScoreRow row) {
        if (row == null || row.getGameDate() == null || row.getAvgScore() == null) {
            return null;
        }
        long players = row.getPlayerCount() == null ? 0L : row.getPlayerCount();
        return new SeasonDailyHighlights.DailyHighlight(row.getGameDate(), row.getAvgScore(), players);
    }

    private static SeasonDailyHighlights.RoundHighlight toRoundHighlight(GuessRepository.RoundAvgScoreRow row) {
        if (row == null || row.getGameDate() == null || row.getAvgScore() == null) {
            return null;
        }
        long players = row.getPlayerCount() == null ? 0L : row.getPlayerCount();
        String appName = row.getAppName() == null || row.getAppName().isBlank()
                ? String.valueOf(row.getAppId())
                : row.getAppName();
        return new SeasonDailyHighlights.RoundHighlight(
                row.getGameDate(),
                row.getRoundIndex(),
                row.getAppId(),
                appName,
                row.getAvgScore(),
                players
        );
    }

    private int tieBreakRoll(Season season, SeasonAwardCategory category, String steamId) {
        int hash = Objects.hash(season.getAwardSeed(), category, steamId);
        int roll = Math.floorMod(hash, TIE_ROLL_MAX);
        return roll == 0 ? 1 : roll;
    }

    private int seasonLengthDays() {
        return Math.max(1, seasonProperties.getLengthDays());
    }

    private static LocalDate todayUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    private static long coerce(Long value) {
        return value == null ? 0 : value;
    }

    private record SeasonStats(String steamId,
                               long totalPoints,
                               long hits,
                               long rounds,
                               long activeDays,
                               double avgPointsPerDay,
                               long longestStreak) {
    }

    public record SeasonReport(SeasonSummary summary,
                               List<PlayerSeasonStat> players) {
    }

    public record SeasonSummary(long totalPlayers,
                                long totalRounds,
                                long totalPoints,
                                long totalHits,
                                double averagePointsPerRound,
                                double averagePointsPerPlayer,
                                double hitRate,
                                double averageActiveDays,
                                long longestStreak,
                                int durationDays,
                                int completedDays,
                                LocalDate dataThrough) {
    }

    public record PlayerSeasonStat(String steamId,
                                   long totalPoints,
                                   long hits,
                                   long rounds,
                                   long activeDays,
                                   double avgPointsPerDay,
                                   double avgPointsPerRound,
                                   long longestStreak) {
    }

    public record SeasonDailyHighlights(DailyHighlight highestAvg,
                                        DailyHighlight lowestAvg,
                                        DailyHighlight busiest,
                                        RoundHighlight easiestRound,
                                        RoundHighlight hardestRound) {

        public record DailyHighlight(LocalDate date,
                                     double avgScore,
                                     long playerCount) {
        }

        public record RoundHighlight(LocalDate date,
                                     int roundIndex,
                                     long appId,
                                     String appName,
                                     double avgScore,
                                     long playerCount) {
        }
    }
}


