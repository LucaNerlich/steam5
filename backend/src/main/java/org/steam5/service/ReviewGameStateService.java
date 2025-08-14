package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewGameStateService {

    private final SteamAppReviewsRepository reviewsRepository;
    private final ReviewGamePickRepository pickRepository;
    private final ReviewGameConfig config;
    private final CacheManager cacheManager;

    @Transactional
    public List<ReviewGamePick> generateDailyPicks() {
        final LocalDate today = LocalDate.now();
        final List<ReviewGamePick> existing = pickRepository.findByPickDate(today);
        if (!existing.isEmpty()) {
            return existing;
        }

        // Exclusion window parameterized
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500 ? LocalDate.of(1970, 1, 1) : today.minusDays(doNotRepeatDays);
        final LocalDate includeAll = LocalDate.of(1970, 1, 1); // relax by allowing any past picks

        final double lowPct = Math.max(0.0, Math.min(1.0, config.getLowPercentile()));
        final double highPct = Math.max(0.0, Math.min(1.0, config.getHighPercentile()));
        final SteamAppReviewsRepository.ReviewThresholds thresholds = reviewsRepository.findPercentileThresholds(lowPct, highPct);
        final int lowThreshold = thresholds == null || thresholds.getLowThreshold() == null ? 100 : thresholds.getLowThreshold();
        final int highThreshold = thresholds == null || thresholds.getHighThreshold() == null ? 10000 : thresholds.getHighThreshold();

        final List<ReviewGamePick> picks = new ArrayList<>(5);
        final Set<Long> chosenIds = new HashSet<>();

        // LOW
        final List<Long> lowIds = reviewsRepository.findRandomLowAppIds(excludeSince, lowThreshold, PageRequest.of(0, 3));
        if (!lowIds.isEmpty()) {
            final Long id = lowIds.getFirst();
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
        } else {
            // relax: allow from all lows ignoring recent
            final List<Long> relaxed = reviewsRepository.findRandomLowAppIds(includeAll, lowThreshold, PageRequest.of(0, 3));
            if (!relaxed.isEmpty()) {
                final Long id = relaxed.getFirst();
                chosenIds.add(id);
                picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
            }
        }

        // HIGH
        final List<Long> highIds = reviewsRepository.findRandomHighAppIds(excludeSince, highThreshold, PageRequest.of(0, 5));
        Optional<Long> highOpt = highIds.stream().filter(id -> !chosenIds.contains(id)).findFirst();
        if (highOpt.isEmpty()) {
            final List<Long> relaxed = reviewsRepository.findRandomHighAppIds(includeAll, highThreshold, PageRequest.of(0, 5));
            highOpt = relaxed.stream().filter(id -> !chosenIds.contains(id)).findFirst();
        }
        highOpt.ifPresent(id -> {
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
        });

        // fill remaining ANY
        while (picks.size() < 5) {
            final List<Long> anyIds = reviewsRepository.findRandomAnyAppIds(excludeSince, PageRequest.of(0, 5));
            Optional<Long> next = anyIds.stream().filter(id -> !chosenIds.contains(id)).findFirst();
            if (next.isEmpty()) {
                List<Long> relaxed = reviewsRepository.findRandomAnyAppIds(includeAll, PageRequest.of(0, 5));
                next = relaxed.stream().filter(id -> !chosenIds.contains(id)).findFirst();
            }
            if (next.isEmpty()) break;
            final Long id = next.get();
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
        }

        final List<ReviewGamePick> saved = pickRepository.saveAll(picks);
        log.info("Generated {} review-game picks for {} (low<= {}, high>= {})", saved.size(), today, lowThreshold, highThreshold);

        // Evict review-game cache only when new picks were created
        if (!saved.isEmpty()) {
            final Cache cache = cacheManager.getCache("review-game");
            if (cache != null) {
                cache.clear();
            }
        }
        return saved;
    }

    public enum Category {HIGH, LOW, ANY}

    @Cacheable(value = "review-game", key = "#appId + 'review-count'")
    public int getTotalReviewCountForApp(Long appId) {
        return reviewsRepository.findById(appId).map(r -> r.getTotalPositive() + r.getTotalNegative()).orElse(0);
    }

    public String inferBucket(int totalReviews) {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) {
            return totalReviews + "+"; // fallback, shouldn't happen with defaults
        }

        int prev = 0;
        for (int b : bounds) {
            if (totalReviews <= b) {
                return (prev == 0 ? "0-" + b : (prev + 1) + "-" + b);
            }
            prev = b;
        }
        return bounds.getLast() + "+";
    }

    public List<String> getBucketLabels() {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) return List.of();

        final ArrayList<String> labels = new java.util.ArrayList<>(bounds.size() + 1);
        int prev = 0;
        for (Integer b : bounds) {
            labels.add((prev == 0 ? "0-" + b : (prev + 1) + "-" + b));
            prev = b;
        }
        labels.add(bounds.getLast() + "+");
        return labels;
    }
}


