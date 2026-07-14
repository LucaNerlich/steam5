package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.review.ReviewBucketStrategy;
import org.steam5.game.review.ReviewGameModule;
import org.steam5.game.review.ReviewGuessEvaluator;
import org.steam5.game.review.ReviewPickGenerator;
import org.steam5.repository.SteamAppReviewsRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ReviewGameStateService {

    public static final int MIN_BUCKET_BOUND = ReviewPickGenerator.MIN_BUCKET_BOUND;

    /** @deprecated use {@link ReviewBucketStrategy} */
    @Deprecated
    public enum BUCKET_STRATEGY {
        RANDOM, EQUAL, LEAN_HIGH, LEAN_LOW, LEAN_CENTER, HIGH, LOW, CENTER;

        ReviewBucketStrategy toReviewStrategy() {
            return ReviewBucketStrategy.valueOf(name());
        }

        static BUCKET_STRATEGY from(final ReviewBucketStrategy strategy) {
            return BUCKET_STRATEGY.valueOf(strategy.name());
        }
    }

    private final DailyGameStateService dailyGameStateService;
    private final ReviewGameModule reviewGameModule;
    private final ReviewPickGenerator pickGenerator;
    private final ReviewGameConfig config;
    private final SteamAppReviewsRepository reviewsRepository;

    public List<ReviewGamePick> generateDailyPicks() {
        return dailyGameStateService.generateDailyPicks(reviewGameModule);
    }

    @Cacheable(value = "review-game", key = "#appId + 'review-count'")
    public int getTotalReviewCountForApp(final Long appId) {
        return reviewsRepository.findById(appId).map(r -> r.getTotalPositive() + r.getTotalNegative()).orElse(0);
    }

    public String inferBucket(final int totalReviews) {
        return ReviewGuessEvaluator.inferBucket(totalReviews, config);
    }

    public List<String> getBucketLabels() {
        return pickGenerator.getBucketLabels();
    }

    public List<String> getBucketTitles() {
        final List<String> labels = getBucketLabels();
        final List<String> titles = config.getBucketTitles();
        if (labels.isEmpty()) {
            return List.of();
        }
        if (titles == null || titles.isEmpty()) {
            return IntStream.range(0, labels.size()).mapToObj(i -> "").toList();
        }
        if (titles.size() == labels.size()) {
            return titles;
        }
        final java.util.ArrayList<String> out = new java.util.ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            out.add(i < titles.size() ? (titles.get(i) == null ? "" : titles.get(i)) : "");
        }
        return out;
    }

    public BUCKET_STRATEGY chooseStrategyForDate(final LocalDate date) {
        return BUCKET_STRATEGY.from(pickGenerator.chooseStrategyForDate(date));
    }

    public int sampleIndex(final double[] weights, final Random rng) {
        return pickGenerator.sampleIndex(weights, rng);
    }

    public List<Integer> planBucketSelection(final BUCKET_STRATEGY strategy,
                                             final int bucketCount,
                                             final int rounds,
                                             final LocalDate date) {
        return pickGenerator.planBucketSelection(strategy.toReviewStrategy(), bucketCount, rounds, date);
    }
}
