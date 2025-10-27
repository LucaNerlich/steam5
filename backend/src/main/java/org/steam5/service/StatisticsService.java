package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

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
        final List<UserLabel> result = new ArrayList<>();

        // Strategy:
        // - Maintain uniqueness: one achievement per user. If a user would win multiple,
        //   assign the first by priority and skip them for subsequent labels.
        // - Use a minimum participation threshold to avoid flukes.
        final int MIN_ROUNDS = 25;

        final Set<String> alreadyAwarded = new HashSet<>();

        // 1) Time-of-day based
        final List<GuessRepository.AvgTimeRow> earliest = guessRepository.findUsersByAvgSubmissionTimeAsc(MIN_ROUNDS, 5);
        final List<GuessRepository.AvgTimeRow> latest = guessRepository.findUsersByAvgSubmissionTimeDesc(MIN_ROUNDS, 5);

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
        final List<GuessRepository.AvgPointsRow> sharp = guessRepository.findUsersByAvgPointsDesc(MIN_ROUNDS, 5);
        for (GuessRepository.AvgPointsRow row : sharp) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.SHARPSHOOTER));
                break;
            }
        }

        // 3) Accuracy/skill based — Bullseye (most perfect rounds)
        final List<GuessRepository.PerfectRoundsRow> bulls = guessRepository.findUsersByPerfectRoundsDesc(MIN_ROUNDS, 5);
        for (GuessRepository.PerfectRoundsRow row : bulls) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.BULLSEYE));
                break;
            }
        }

        // 4) Accuracy/skill based — Perfect Day (most perfect days)
        final List<GuessRepository.PerfectDaysRow> pdays = guessRepository.findUsersByPerfectDaysDesc(MIN_ROUNDS, 5);
        for (GuessRepository.PerfectDaysRow row : pdays) {
            final String steamId = row.getSteamId();
            if (alreadyAwarded.add(steamId)) {
                result.add(new UserLabel(steamId, UserAchievement.PERFECT_DAY));
                break;
            }
        }

        return Collections.unmodifiableList(result);
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


