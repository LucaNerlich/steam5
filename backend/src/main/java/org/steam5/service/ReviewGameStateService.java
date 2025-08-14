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
import org.steam5.repository.DailyPickLockRepository;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewGameStateService {

    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppReviewsFetcher reviewsFetcher;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final ReviewGamePickRepository pickRepository;
    private final DailyPickLockRepository pickLockRepository;
    private final ExcludedAppRepository excludedAppRepository;
    private final ReviewGameConfig config;
    private final CacheManager cacheManager;

    @Transactional
    public List<ReviewGamePick> generateDailyPicks() {
        final LocalDate today = LocalDate.now();
        final List<ReviewGamePick> existing = pickRepository.findByPickDate(today);
        if (!existing.isEmpty()) {
            return existing;
        }

        // Try to acquire a per-day lock to prevent concurrent generation (job vs endpoint)
        final int acquired = pickLockRepository.tryAcquire(today);
        if (acquired == 0) {
            // Another generator is running/just ran; re-read and return existing
            final List<ReviewGamePick> concurrent = pickRepository.findByPickDate(today);
            if (!concurrent.isEmpty()) return concurrent;
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

        // Helper to validate an appId by fetching details; returns true if usable, otherwise records exclusion
        final Predicate<Long> validateApp = appId -> {
            try {
                boolean ok = detailsFetcher.fetchForAppId(appId);
                if (!ok) {
                    excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId, "details fetch failed or success=false", OffsetDateTime.now()));
                }
                return ok;
            } catch (Exception e) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId, "details fetch error: " + e.getMessage(), OffsetDateTime.now()));
                return false;
            }
        };

        // LOW
        final List<Long> lowIds = reviewsRepository.findRandomLowAppIds(excludeSince, lowThreshold, PageRequest.of(0, 5));
        for (Long id : lowIds) {
            if (validateApp.test(id)) {
                chosenIds.add(id);
                picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                break;
            }
        }
        if (picks.stream().noneMatch(p -> true)) {
            final List<Long> relaxed = reviewsRepository.findRandomLowAppIds(includeAll, lowThreshold, PageRequest.of(0, 10));
            for (Long id : relaxed) {
                if (validateApp.test(id)) {
                    chosenIds.add(id);
                    picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                    break;
                }
            }
        }

        // HIGH
        final List<Long> highIds = reviewsRepository.findRandomHighAppIds(excludeSince, highThreshold, PageRequest.of(0, 5));
        boolean pickedHigh = false;
        for (Long id : highIds) {
            if (!chosenIds.contains(id) && validateApp.test(id)) {
                chosenIds.add(id);
                picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                pickedHigh = true;
                break;
            }
        }
        if (!pickedHigh) {
            final List<Long> relaxed = reviewsRepository.findRandomHighAppIds(includeAll, highThreshold, PageRequest.of(0, 10));
            for (Long id : relaxed) {
                if (!chosenIds.contains(id) && validateApp.test(id)) {
                    chosenIds.add(id);
                    picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                    break;
                }
            }
        }

        // fill remaining ANY
        while (picks.size() < 5) {
            final List<Long> anyIds = reviewsRepository.findRandomAnyAppIds(excludeSince, PageRequest.of(0, 10));
            boolean added = false;
            for (Long id : anyIds) {
                if (!chosenIds.contains(id) && validateApp.test(id)) {
                    chosenIds.add(id);
                    picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                    added = true;
                    break;
                }
            }
            if (!added) {
                final List<Long> relaxed = reviewsRepository.findRandomAnyAppIds(includeAll, PageRequest.of(0, 10));
                for (Long id : relaxed) {
                    if (!chosenIds.contains(id) && validateApp.test(id)) {
                        chosenIds.add(id);
                        picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                        added = true;
                        break;
                    }
                }
            }
            if (!added) break;
        }

        final List<ReviewGamePick> saved = pickRepository.saveAll(picks);
        log.info("Generated {} review-game picks for {} (low<= {}, high>= {})", saved.size(), today, lowThreshold, highThreshold);

        // Evict review-game cache only when new picks were created
        if (!saved.isEmpty()) {
            // Update reviews and details for the selected appIds
            for (ReviewGamePick p : saved) {
                try {
                    reviewsFetcher.fetchForAppId(p.getAppId());
                } catch (Exception e) {
                    log.warn("Failed to refresh reviews for picked appId {}: {}", p.getAppId(), e.getMessage());
                }
                try {
                    detailsFetcher.fetchForAppId(p.getAppId());
                } catch (Exception e) {
                    log.warn("Failed to refresh details for picked appId {}: {}", p.getAppId(), e.getMessage());
                }
            }

            // Clear game cache
            final Cache cache = cacheManager.getCache("review-game");
            if (cache != null) {
                cache.clear();
            }
        }
        return saved;
    }

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
        int prev = 1;
        for (Integer b : bounds) {
            labels.add((prev == 1 ? "1-" + b : (prev + 1) + "-" + b));
            prev = b;
        }
        labels.add(bounds.getLast() + "+");
        return labels;
    }
}


