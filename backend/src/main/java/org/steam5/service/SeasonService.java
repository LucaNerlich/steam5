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

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response"}, allEntries = true)
    public Season ensureSeasonForDate(LocalDate date) {
        Objects.requireNonNull(date, "date");

        return seasonRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
                .orElseGet(() -> createSeasonsUntil(date));
    }

    @Transactional
    @CacheEvict(value = {"season-current-response", "season-list-response"}, allEntries = true)
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
    @CacheEvict(value = {"season-current-response", "season-list-response"}, allEntries = true)
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
    @CacheEvict(value = {"season-current-response", "season-list-response", "season-awards-response", "season-awards", "player-awards"}, allEntries = true)
    public Season finalizeSeason(Season season) {
        Objects.requireNonNull(season, "season");
        Season managed = seasonRepository.findById(season.getId()).orElse(season);
        log.info("Finalizing season #{} ({} - {})", managed.getSeasonNumber(), managed.getStartDate(), managed.getEndDate());

        List<GuessRepository.SeasonStatRow> rows = guessRepository.findSeasonStats(managed.getStartDate(), managed.getEndDate());
        Map<String, List<LocalDate>> participationDates = buildSeasonParticipationDates(managed.getStartDate(), managed.getEndDate());
        Map<String, SeasonStats> statsByPlayer = rows.stream()
                .map(row -> buildSeasonStats(row, participationDates.getOrDefault(row.getSteamId(), List.of())))
                .collect(Collectors.toMap(SeasonStats::steamId, Function.identity()));

        awardResultRepository.deleteAllBySeasonId(managed.getId());

        List<SeasonAwardResult> newResults = new ArrayList<>();
        for (SeasonAwardCategory category : seasonProperties.getAwards().getCategories()) {
            newResults.addAll(generateAwardsForCategory(managed, category, statsByPlayer));
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
                                                              Map<String, SeasonStats> statsByPlayer) {
        if (statsByPlayer.isEmpty()) {
            return List.of();
        }

        final int maxPlacements = Math.max(1, seasonProperties.getAwards().getMaxPlacements());
        Comparator<SeasonStats> comparator = Comparator
                .comparingLong((SeasonStats stats) -> metricValue(category, stats))
                .reversed()
                .thenComparingInt(stats -> tieBreakRoll(season, category, stats.steamId()));

        List<SeasonStats> ordered = statsByPlayer.values().stream()
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

    private int tieBreakRoll(Season season, SeasonAwardCategory category, String steamId) {
        int hash = Objects.hash(season.getAwardSeed(), category, steamId);
        int roll = Math.floorMod(hash, TIE_ROLL_MAX);
        return roll == 0 ? 1 : roll;
    }

    private int seasonLengthDays() {
        return Math.max(1, seasonProperties.getLengthDays());
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
}


