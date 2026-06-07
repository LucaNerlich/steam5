package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SteamAppDetailRepository detailRepository;
    private final ReviewsBucketRepository reviewsBucketRepository;
    private final ReviewGamePickRepository reviewGamePickRepository;
    private final GuessRepository guessRepository;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;

    @Cacheable(value = "stats-long", key = "'stats-genres-'+#limit", unless = "#result == null")
    public List<LabelCount> topGenres(int limit) {
        return detailRepository.topGenres(limit).stream()
                .map(p -> new LabelCount(p.getLabel(), p.getCount()))
                .toList();
    }

    @Cacheable(value = "stats-long", key = "'stats-categories-'+#limit", unless = "#result == null")
    public List<LabelCount> topCategories(int limit) {
        return detailRepository.topCategories(limit).stream()
                .map(p -> new LabelCount(p.getLabel(), p.getCount()))
                .toList();
    }

    @Cacheable(value = "stats-long", key = "'stats-review-buckets-'+#mode", unless = "#result == null")
    public List<Bucket> reviewBuckets(BucketMode mode) {
        return switch (mode) {
            case EQUAL_WIDTH -> reviewsBucketRepository.equalWidth().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case EQUAL_COUNT -> reviewsBucketRepository.equalCount().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case LINEAR_WINSORIZED -> reviewsBucketRepository.linearWinsorized().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
            case LOG_SPACE -> reviewsBucketRepository.logSpace().stream()
                    .map(b -> new Bucket(b.bucket(), b.lower(), b.upper(), b.label(), b.countInBucket()))
                    .toList();
        };
    }

    @Cacheable(value = "one-day", key = "'stats-user-labels'", unless = "#result == null")
    public List<UserLabel> getUserAchievements() {
        return getUserAchievementsForTimeframe(Timeframe.ALL_TIME);
    }

    @Cacheable(value = "stats-hourly", key = "'stats-user-labels-weekly'", unless = "#result == null")
    public List<UserLabel> getUserAchievementsWeekly() {
        return getUserAchievementsForTimeframe(Timeframe.WEEKLY);
    }

    @Cacheable(value = "stats-short", key = "'stats-user-labels-daily'", unless = "#result == null")
    public List<UserLabel> getUserAchievementsDaily() {
        return getUserAchievementsForTimeframe(Timeframe.DAILY);
    }

    @Cacheable(value = "stats-hourly", key = "'stats-user-labels-monthly'", unless = "#result == null")
    public List<UserLabel> getUserAchievementsMonthly() {
        return getUserAchievementsForTimeframe(Timeframe.MONTHLY);
    }

    public List<UserLabel> getUserAchievementsSeason() {
        final LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        final Season season = seasonService.findSeasonContaining(today)
                .orElseGet(() -> seasonService.ensureSeasonForDate(today));
        final LocalDate asOfDate = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        final String cacheKey = "stats-user-labels-season:" + season.getSeasonNumber() + ":" + asOfDate;
        final Cache cache = cacheManager.getCache("stats-hourly");
        if (cache != null) {
            final Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<UserLabel> cachedEntries = (List<UserLabel>) cached;
                return cachedEntries;
            }
        }

        final List<UserLabel> result = getUserAchievementsForTimeframe(Timeframe.SEASON, season.getStartDate(), asOfDate);
        if (cache != null) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    private List<UserLabel> getUserAchievementsForTimeframe(Timeframe timeframe) {
        return getUserAchievementsForTimeframe(timeframe, null, null);
    }

    private List<UserLabel> getUserAchievementsForTimeframe(Timeframe timeframe, LocalDate startOverride, LocalDate endOverride) {
        final List<UserLabel> result = new ArrayList<>();

        // Strategy:
        // - Maintain uniqueness: one achievement per user. If a user would win multiple,
        //   assign the first by priority and skip them for subsequent labels.
        // - Use a minimum participation threshold to avoid flukes.
        // - Note: Achievements are filtered on the frontend to only show users who appear
        //   on the leaderboard, so this threshold should be low enough to not exclude
        //   legitimate leaderboard participants.
        final int MIN_ROUNDS = switch (timeframe) {
            case ALL_TIME -> 10;  // Reduced from 35 - leaderboard has no minimum
            case MONTHLY -> 5;    // Reduced from 25 - leaderboard has no minimum
            case WEEKLY -> 3;     // Reduced from 15 - leaderboard has no minimum
            case DAILY -> 1;      // Reduced from 5 - leaderboard has no minimum
            case SEASON -> 5;
        };

        final Set<String> alreadyAwarded = new HashSet<>();

        // Calculate date range for timeframe
        final LocalDate today = LocalDate.now();
        final LocalDate startDate;
        final LocalDate endDate;

        switch (timeframe) {
            case ALL_TIME -> {
                // Use all-time queries (no date range)
                processAchievements(result, alreadyAwarded, MIN_ROUNDS, null, null);
                return Collections.unmodifiableList(result);
            }
            case MONTHLY -> {
                // Last 30 days including today
                startDate = today.minusDays(29);
                endDate = today;
            }
            case WEEKLY -> {
                // Last 7 days including today
                startDate = today.minusDays(6);
                endDate = today;
            }
            case DAILY -> {
                // Just today
                startDate = today;
                endDate = today;
            }
            case SEASON -> {
                if (startOverride == null || endOverride == null) {
                    return Collections.emptyList();
                }
                startDate = startOverride;
                endDate = endOverride;
            }
            default -> {
                return Collections.emptyList();
            }
        }

        if (endDate.isBefore(startDate)) {
            return Collections.emptyList();
        }
        processAchievements(result, alreadyAwarded, MIN_ROUNDS, startDate, endDate);
        return Collections.unmodifiableList(result);
    }

    private void processAchievements(final List<UserLabel> result, final Set<String> alreadyAwarded,
                                     final int minRounds, final LocalDate startDate, final LocalDate endDate) {
        // Time-of-day
        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByAvgSubmissionTimeAsc(minRounds),
                      () -> guessRepository.findUsersByAvgSubmissionTimeAscInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.AvgTimeRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.EARLY_BIRD, row.getAvgMinutes(), null, null, null, null));

        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByAvgSubmissionTimeDesc(minRounds),
                      () -> guessRepository.findUsersByAvgSubmissionTimeDescInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.AvgTimeRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.NIGHT_OWL, row.getAvgMinutes(), null, null, null, null));

        // Accuracy / skill
        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByAvgPointsDesc(minRounds),
                      () -> guessRepository.findUsersByAvgPointsDescInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.AvgPointsRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.SHARPSHOOTER, null, row.getAvgPoints(), null, null, null));

        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByPerfectRoundsDesc(minRounds),
                      () -> guessRepository.findUsersByPerfectRoundsDescInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.PerfectRoundsRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.BULLSEYE, null, null, row.getPerfects(), null, null));

        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByPerfectDaysDesc(minRounds),
                      () -> guessRepository.findUsersByPerfectDaysDescInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.PerfectDaysRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.PERFECT_DAY, null, null, null, row.getPerfectDays(), null));

        // Speed
        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByDailyTimeDiffAsc(minRounds),
                      () -> guessRepository.findUsersByDailyTimeDiffAscInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.DailyTimeDiffRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.CHEETAH, null, null, null, null, row.getTotalSeconds()));

        awardFirst(result, alreadyAwarded,
                fetch(() -> guessRepository.findUsersByDailyTimeDiffDesc(minRounds),
                      () -> guessRepository.findUsersByDailyTimeDiffDescInRange(startDate, endDate, minRounds),
                      startDate),
                GuessRepository.DailyTimeDiffRow::getSteamId,
                row -> new UserLabel(row.getSteamId(), UserAchievement.SLOTH, null, null, null, null, row.getTotalSeconds()));
    }

    /** Returns the all-time query result when {@code startDate} is null, the ranged result otherwise. */
    private static <R> List<R> fetch(final Supplier<List<R>> allTime,
                                     final Supplier<List<R>> ranged,
                                     final LocalDate startDate) {
        return startDate == null ? allTime.get() : ranged.get();
    }

    /** Awards the first non-already-awarded user in {@code candidates}. No-op when the list is empty. */
    private static <R> void awardFirst(final List<UserLabel> result, final Set<String> alreadyAwarded,
                                       final List<R> candidates,
                                       final Function<R, String> getSteamId,
                                       final Function<R, UserLabel> toLabel) {
        for (final R row : candidates) {
            final String steamId = getSteamId.apply(row);
            if (alreadyAwarded.add(steamId)) {
                result.add(toLabel.apply(row));
                break;
            }
        }
    }

    public enum Timeframe {
        ALL_TIME,
        SEASON,
        MONTHLY,
        WEEKLY,
        DAILY
    }

    public enum BucketMode {EQUAL_WIDTH, EQUAL_COUNT, LINEAR_WINSORIZED, LOG_SPACE}

    public record LabelCount(String label, long count) {
    }

    public record Bucket(int bucket, Long lower, Long upper, String label, long count) {
    }

    public enum UserAchievement {
        EARLY_BIRD,    // plays the earliest, on avg
        NIGHT_OWL,    // plays the latest, on avg
        SHARPSHOOTER, // highest average points per guess
        BULLSEYE,     // most perfect rounds (points = 5)
        PERFECT_DAY,  // most days with perfect total points
        CHEETAH,      // least time between first and last guess per day (summed)
        SLOTH         // most time between first and last guess per day (summed)
    }

    // "Achievement" Labels with metrics
    public record UserLabel(
            String steamId,
            UserAchievement userAchievement,
            // Metrics - only one will be populated based on achievement type
            Double avgMinutes,      // For EARLY_BIRD, NIGHT_OWL
            Double avgPoints,       // For SHARPSHOOTER
            Long perfectRounds,      // For BULLSEYE
            Long perfectDays,        // For PERFECT_DAY
            Long totalSeconds        // For CHEETAH, SLOTH
    ) {
    }

    @Cacheable(value = "stats-hourly", key = "'top-games-by-reviews-' + #limit", unless = "#result == null")
    public List<TopGameByReviews> getTopGamesByReviewCount(int limit) {
        final List<ReviewGamePickRepository.TopGameByReviewsRow> rows = reviewGamePickRepository.findTopGamesByReviewCount(limit);
        final Set<LocalDate> pickDates = rows.stream()
                .map(ReviewGamePickRepository.TopGameByReviewsRow::getPickDate)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final Map<LocalDate, List<ReviewGamePick>> picksByDate = pickDates.isEmpty()
                ? Map.of()
                : reviewGamePickRepository.findByPickDateIn(pickDates).stream()
                .collect(Collectors.groupingBy(ReviewGamePick::getPickDate));
        picksByDate.values().forEach(picks -> picks.sort(
                Comparator.comparing(ReviewGamePick::getCreatedAt).thenComparing(ReviewGamePick::getId)
        ));

        return rows.stream()
                .map(row -> {
                    final LocalDate pickDate = row.getPickDate();
                    // Calculate round index for the most recent pick date
                    Integer roundIndex = null;
                    if (pickDate != null && row.getAppId() != null) {
                        final List<ReviewGamePick> sortedPicks = picksByDate.getOrDefault(pickDate, List.of());
                        // Find the index of the pick for this app
                        for (int i = 0; i < sortedPicks.size(); i++) {
                            if (sortedPicks.get(i).getAppId().equals(row.getAppId())) {
                                roundIndex = i + 1; // 1-based index
                                break;
                            }
                        }
                    }
                    return new TopGameByReviews(
                            row.getAppId(),
                            row.getName() != null ? row.getName() : "Unknown",
                            row.getTotalReviews() != null ? row.getTotalReviews() : 0L,
                            pickDate != null ? pickDate.toString() : null,
                            roundIndex
                    );
                })
                .toList();
    }

    @Cacheable(value = "stats-hourly", key = "'daily-avg-scores'", unless = "#result == null")
    public DailyAvgScoreStats getDailyAvgScoreStats() {
        final List<GuessRepository.DailyAvgScoreRow> allScores = guessRepository.findDailyAvgScoresDesc();
        if (allScores.isEmpty()) {
            return new DailyAvgScoreStats(null, null);
        }
        final GuessRepository.DailyAvgScoreRow highest = allScores.get(0);
        final List<GuessRepository.DailyAvgScoreRow> lowestScores = guessRepository.findDailyAvgScoresAsc();
        final GuessRepository.DailyAvgScoreRow lowest = lowestScores.isEmpty() ? null : lowestScores.get(0);
        return new DailyAvgScoreStats(
                highest != null ? new DailyAvgScore(highest.getGameDate(), highest.getAvgScore(), highest.getPlayerCount()) : null,
                lowest != null ? new DailyAvgScore(lowest.getGameDate(), lowest.getAvgScore(), lowest.getPlayerCount()) : null
        );
    }

    public record TopGameByReviews(Long appId, String name, Long totalReviews, String pickDate, Integer roundIndex) {
    }

    public record DailyAvgScore(LocalDate date, Double avgScore, Long playerCount) {
    }

    public record DailyAvgScoreStats(DailyAvgScore highest, DailyAvgScore lowest) {
    }

    public record GameStatistics(List<TopGameByReviews> topGamesByReviewCount, DailyAvgScoreStats dailyAvgScores) {
    }
}


