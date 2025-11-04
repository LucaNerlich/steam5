package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SteamAppDetailRepository detailRepository;
    private final ReviewsBucketRepository reviewsBucketRepository;
    private final ReviewGamePickRepository reviewGamePickRepository;
    private final GuessRepository guessRepository;

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

    private List<UserLabel> getUserAchievementsForTimeframe(Timeframe timeframe) {
        final List<UserLabel> result = new ArrayList<>();

        // Strategy:
        // - Maintain uniqueness: one achievement per user. If a user would win multiple,
        //   assign the first by priority and skip them for subsequent labels.
        // - Use a minimum participation threshold to avoid flukes.
        final int MIN_ROUNDS = switch (timeframe) {
            case ALL_TIME -> 35;
            case MONTHLY -> 25;
            case WEEKLY -> 15;
            case DAILY -> 5;
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
            default -> {
                return Collections.emptyList();
            }
        }

        processAchievements(result, alreadyAwarded, MIN_ROUNDS, startDate, endDate);
        return Collections.unmodifiableList(result);
    }

    private void processAchievements(List<UserLabel> result, Set<String> alreadyAwarded,
                                     int minRounds, LocalDate startDate, LocalDate endDate) {
        // 1) Time-of-day based
        final List<GuessRepository.AvgTimeRow> earliest;
        final List<GuessRepository.AvgTimeRow> latest;

        if (startDate == null) {
            earliest = guessRepository.findUsersByAvgSubmissionTimeAsc(minRounds);
            latest = guessRepository.findUsersByAvgSubmissionTimeDesc(minRounds);
        } else {
            earliest = guessRepository.findUsersByAvgSubmissionTimeAscInRange(startDate, endDate, minRounds);
            latest = guessRepository.findUsersByAvgSubmissionTimeDescInRange(startDate, endDate, minRounds);
        }

        if (!earliest.isEmpty()) {
            for (GuessRepository.AvgTimeRow row : earliest) {
                final String steamId = row.getSteamId();
                if (alreadyAwarded.add(steamId)) {
                    result.add(new UserLabel(steamId, UserAchievement.EARLY_BIRD));
                    break;
                }
            }
        }

        if (!latest.isEmpty()) {
            for (GuessRepository.AvgTimeRow row : latest) {
                final String steamId = row.getSteamId();
                if (alreadyAwarded.add(steamId)) {
                    result.add(new UserLabel(steamId, UserAchievement.NIGHT_OWL));
                    break;
                }
            }
        }

        // 2) Accuracy/skill based — Sharpshooter (highest average points)
        final List<GuessRepository.AvgPointsRow> sharp = startDate == null
                ? guessRepository.findUsersByAvgPointsDesc(minRounds)
                : guessRepository.findUsersByAvgPointsDescInRange(startDate, endDate, minRounds);

        for (GuessRepository.AvgPointsRow row : sharp) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.SHARPSHOOTER));
                break;
            }
        }

        // 3) Accuracy/skill based — Bullseye (most perfect rounds)
        final List<GuessRepository.PerfectRoundsRow> bulls = startDate == null
                ? guessRepository.findUsersByPerfectRoundsDesc(minRounds)
                : guessRepository.findUsersByPerfectRoundsDescInRange(startDate, endDate, minRounds);

        for (GuessRepository.PerfectRoundsRow row : bulls) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.BULLSEYE));
                break;
            }
        }

        // 4) Accuracy/skill based — Perfect Day (most perfect days)
        final List<GuessRepository.PerfectDaysRow> pdays = startDate == null
                ? guessRepository.findUsersByPerfectDaysDesc(minRounds)
                : guessRepository.findUsersByPerfectDaysDescInRange(startDate, endDate, minRounds);

        for (GuessRepository.PerfectDaysRow row : pdays) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.PERFECT_DAY));
                break;
            }
        }
    }

    public enum Timeframe {
        ALL_TIME,
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
        PERFECT_DAY   // most days with perfect total points
    }

    // "Achievement" Labels
    public record UserLabel(String steamId, UserAchievement userAchievement) {
    }
}


