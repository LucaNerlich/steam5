package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.review.ReviewBucketStrategy;
import org.steam5.game.year.YearGameConfig;
import org.steam5.game.year.YearGameModule;
import org.steam5.game.year.YearGamePick;
import org.steam5.game.year.YearGuessEvaluator;
import org.steam5.game.year.YearPickGenerator;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.util.ReleaseDateParser;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class YearGameStateService {

    private final DailyGameStateService dailyGameStateService;
    private final YearGameModule yearGameModule;
    private final YearPickGenerator pickGenerator;
    private final YearGameConfig config;
    private final SteamAppDetailRepository detailRepository;

    public List<YearGamePick> generateDailyPicks() {
        return dailyGameStateService.generateDailyPicks(yearGameModule);
    }

    @Cacheable(value = "year-game", key = "#appId + ':release-year'")
    public int getReleaseYearForApp(final Long appId) {
        return detailRepository.findByAppId(appId)
                .flatMap(detail -> ReleaseDateParser.parseYear(detail.getReleaseDate()))
                .orElse(0);
    }

    public String inferBucket(final int releaseYear) {
        return YearGuessEvaluator.inferBucket(releaseYear, config);
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

    public ReviewBucketStrategy chooseStrategyForDate(final LocalDate date) {
        return pickGenerator.chooseStrategyForDate(date);
    }

    public int sampleIndex(final double[] weights, final Random rng) {
        return pickGenerator.sampleIndex(weights, rng);
    }

    public List<Integer> planBucketSelection(final ReviewBucketStrategy strategy,
                                             final int bucketCount,
                                             final int rounds,
                                             final LocalDate date) {
        return pickGenerator.planBucketSelection(strategy, bucketCount, rounds, date);
    }
}
