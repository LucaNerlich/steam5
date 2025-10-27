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
        // - Compute average submission time (minutes since midnight) per user across all guesses
        // - Only consider users with at least MIN_ROUNDS guesses
        // - Award EARLY_BIRD to the user with the smallest average time
        // - Award NIGHT_OWL to the user with the largest average time
        // - Ensure a single user cannot get more than one achievement label
        final int MIN_ROUNDS = 25;

        // Fetch a few candidates to handle the case where the same user would top both lists
        final List<GuessRepository.AvgTimeRow> earliest = guessRepository.findUsersByAvgSubmissionTimeAsc(MIN_ROUNDS, 5);
        final List<GuessRepository.AvgTimeRow> latest = guessRepository.findUsersByAvgSubmissionTimeDesc(MIN_ROUNDS, 5);

        final Set<String> alreadyAwarded = new HashSet<>();

        if (!earliest.isEmpty()) {
            final String steamId = earliest.get(0).getSteamId();
            result.add(new UserLabel(steamId, UserAchievement.EARLY_BIRD));
            alreadyAwarded.add(steamId);
        }

        if (!latest.isEmpty()) {
            String nightOwlId = null;
            for (GuessRepository.AvgTimeRow row : latest) {
                if (!alreadyAwarded.contains(row.getSteamId())) {
                    nightOwlId = row.getSteamId();
                    break;
                }
            }
            if (nightOwlId != null) {
                result.add(new UserLabel(nightOwlId, UserAchievement.NIGHT_OWL));
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
        EARLY_BIRD, // plays the earliest, on avg
        NIGHT_OWL // plays the latest, on avg
    }

    // "Achievement" Labels
    public record UserLabel(String steamId, UserAchievement userAchievement) {
    }
}


