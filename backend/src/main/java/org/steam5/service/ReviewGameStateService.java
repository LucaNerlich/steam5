package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.SteamAppReviews;
import org.steam5.http.SteamApiException;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.DailyPickLockRepository;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewGameStateService {

    public static final int MIN_BUCKET_BOUND = 1;

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
            // Another generator is running/just ran; wait briefly for it to finish then return existing
            for (int i = 0; i < 20; i++) { // ~2s total
                final List<ReviewGamePick> concurrent = pickRepository.findByPickDate(today);
                if (!concurrent.isEmpty()) return concurrent;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            // Do not attempt to generate if lock not acquired
            return pickRepository.findByPickDate(today);
        }

        log.info("Generating review-game picks for {}", today);

        // Exclusion window parameterized
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500 ? LocalDate.of(1970, MIN_BUCKET_BOUND, MIN_BUCKET_BOUND) : today.minusDays(doNotRepeatDays);

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
            } catch (SteamApiException sae) {
                // Do not permanently exclude apps on rate limiting; abort generation instead to respect Steam API
                if (sae.getStatusCode() == 429) {
                    log.warn("Steam API rate limited (429) while validating appId {}. Aborting pick generation without exclusion.", appId);
                    throw new RuntimeException(sae); // propagate to stop execution per policy
                }
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId, "details fetch error: HTTP " + sae.getStatusCode(), OffsetDateTime.now()));
                return false;
            } catch (Exception e) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId, "details fetch error: " + e.getMessage(), OffsetDateTime.now()));
                return false;
            }
        };

        // Build buckets from configured boundaries
        final List<Integer> bounds = config.getBucketBoundaries();
        final List<int[]> bucketRanges = new ArrayList<>(5);
        if (bounds != null && !bounds.isEmpty()) {
            int prev = MIN_BUCKET_BOUND;
            for (int b : bounds) {
                bucketRanges.add(new int[]{prev, b});
                prev = b + 1;
            }
            bucketRanges.add(new int[]{bounds.getLast() + 1, Integer.MAX_VALUE});
        } else {
            bucketRanges.add(new int[]{MIN_BUCKET_BOUND, 100});
            bucketRanges.add(new int[]{101, 1000});
            bucketRanges.add(new int[]{1001, 10000});
            bucketRanges.add(new int[]{10001, 100000});
            bucketRanges.add(new int[]{100001, Integer.MAX_VALUE});
        }

        // Validate bucket count: at least 5; if more than 5, the count must be odd
        final int bucketCount = bucketRanges.size();
        if (bucketCount < 5 || (bucketCount > 5 && bucketCount % 2 == 0)) {
            throw new IllegalStateException("Invalid bucket configuration: " + bucketCount + " buckets. Must be at least 5 and odd when > 5.");
        }

        // Decide on strategy for today (seeded for reproducibility)
        final BUCKET_STRATEGY strategy = chooseStrategyForDate(today);
        log.info("Bucket strategy for {}: {}", today, strategy);

        final List<Integer> bucketOrder = planBucketSelection(strategy, bucketRanges.size(), bucketRanges.size(), today);

        // For logging, prepare labels for each bucket
        final List<String> labels = getBucketLabels();

        // Pick apps according to planned buckets, with fallback to ANY while preserving uniqueness
        int round = 1;
        for (Integer bucketIndex : bucketOrder) {
            final int[] range = bucketRanges.get(bucketIndex);
            boolean added = false;
            final List<Long> candidates = (range[1] == Integer.MAX_VALUE)
                    ? reviewsRepository.findRandomGte(excludeSince, range[0], PageRequest.of(0, 8))
                    : reviewsRepository.findRandomBetween(excludeSince, range[0], range[1], PageRequest.of(0, 8));

            for (Long id : candidates) {
                if (!chosenIds.contains(id) && validateApp.test(id)) {
                    chosenIds.add(id);
                    picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                    log.info("Round {}: bucket {} (range {}-{}) -> picked appId {}", round, (labels.isEmpty() ? bucketIndex : labels.get(bucketIndex)), range[0], range[1] == Integer.MAX_VALUE ? "∞" : String.valueOf(range[1]), id);
                    added = true;
                    break;
                }
            }

            if (!added) {
                // fallback ANY
                final List<Long> anyIds = reviewsRepository.findRandomAnyAppIds(excludeSince, PageRequest.of(0, 10));
                for (Long id : anyIds) {
                    if (!chosenIds.contains(id) && validateApp.test(id)) {
                        chosenIds.add(id);
                        picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                        log.info("Round {}: bucket {} fallback ANY -> picked appId {}", round, (labels.isEmpty() ? bucketIndex : labels.get(bucketIndex)), id);
                        break;
                    }
                }
            }
            round++;
        }

        // Shuffle picks to avoid deterministic bucket order per round
        Collections.shuffle(picks);
        final List<ReviewGamePick> saved = pickRepository.saveAll(picks);
        log.info("Generated {} review-game picks for {}", saved.size(), today);

        // Evict review-game cache only when new picks were created
        if (!saved.isEmpty()) {
            // Update reviews and details for the selected appIds
            for (ReviewGamePick p : saved) {
                // Refresh reviews only if data is stale beyond configured threshold
                try {
                    final int days = config.getMinReviewsFreshDays();
                    if (days > 0) {
                        final OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
                        final SteamAppReviews existingReviews = reviewsRepository.findById(p.getAppId()).orElse(null);
                        final boolean needsRefresh = existingReviews == null
                                || existingReviews.getUpdatedAt() == null
                                || existingReviews.getUpdatedAt().isBefore(cutoff);
                        if (needsRefresh) {
                            log.info("Refreshing reviews for picked appId {} (cutoff={}, existingAt={})", p.getAppId(), cutoff, existingReviews != null ? existingReviews.getUpdatedAt() : null);
                            reviewsFetcher.fetchForAppId(p.getAppId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to conditionally refresh reviews for picked appId {}: {}", p.getAppId(), e.getMessage());
                }

                try {
                    detailsFetcher.fetchForAppId(p.getAppId());
                } catch (Exception e) {
                    log.warn("Failed to refresh details for picked appId {}: {}", p.getAppId(), e.getMessage());
                }

                // Publish an event to enqueue BlurhashScreenshotsJob asynchronously for this appId
                eventPublisher.publishEvent(new BlurhashEncodeRequested(p.getAppId(), null, BlurhashEnqueueListener.Type.SCREENSHOT));
            }

            // Clear game cache
            final Cache cache = cacheManager.getCache("review-game");
            if (cache != null) {
                cache.clear();
            }
        }
        return saved;
    }

    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppReviewsFetcher reviewsFetcher;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final ReviewGamePickRepository pickRepository;
    private final DailyPickLockRepository pickLockRepository;
    private final ExcludedAppRepository excludedAppRepository;
    private final ReviewGameConfig config;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public List<String> getBucketLabels() {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) return List.of();

        final ArrayList<String> labels = new ArrayList<>(bounds.size() + MIN_BUCKET_BOUND);
        int prev = MIN_BUCKET_BOUND;
        for (Integer b : bounds) {
            labels.add((prev == MIN_BUCKET_BOUND ? "1-" + b : (prev + MIN_BUCKET_BOUND) + "-" + b));
            prev = b;
        }
        labels.add(bounds.getLast() + "+");
        return labels;
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

        int prev = MIN_BUCKET_BOUND;
        for (int b : bounds) {
            if (totalReviews <= b) {
                return (prev == MIN_BUCKET_BOUND ? "1-" + b : (prev + MIN_BUCKET_BOUND) + "-" + b);
            }
            prev = b;
        }
        return bounds.getLast() + "+";
    }

    public List<String> getBucketTitles() {
        final List<String> labels = getBucketLabels();
        final List<String> titles = config.getBucketTitles();
        if (labels.isEmpty()) return List.of();
        if (titles == null || titles.isEmpty()) {
            // default to empty strings of same length
            return IntStream.range(0, labels.size()).mapToObj(i -> "").toList();
        }
        if (titles.size() == labels.size()) return titles;
        // normalize size by truncating or padding with empty strings
        final ArrayList<String> out = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            out.add(i < titles.size() ? (titles.get(i) == null ? "" : titles.get(i)) : "");
        }
        return out;
    }

    /**
     * 0. Validate that we have at least 5 buckets; if there are more than 5, the total number of buckets must be odd. Abort with an exception otherwise.
     * 1. Pick a random bucket strategy
     * 2. Pick a random appId for each round according to the chosen bucket strategy
     * 3. Save picks.
     * 4. Refresh reviews and details for the picked appIds.
     * 5. Publish an event to enqueue BlurhashScreenshotsJob asynchronously for each appId.
     * 6. Clear game cache.
     *
     * The bucket strategies are:
     * - RANDOM: For each round, choose a bucket uniformly at random, then pick a random app from that bucket.
     * - EQUAL: Aim for an equal number of picks per bucket across the rounds (exactly one per bucket when rounds == bucket count).
     * - LEAN_HIGH: Assign higher selection probability to higher buckets (e.g., skew toward the top buckets when there are five).
     * - LEAN_LOW: Assign higher selection probability to lower buckets (e.g., skew toward the bottom buckets when there are five).
     * - LEAN_CENTER: Assign higher selection probability to the center bucket (e.g., skew toward the middle when there are five).
     * - HIGH: Reserve multiple focused picks from the top two buckets; select remaining rounds according to the baseline distribution.
     * - LOW: Reserve multiple focused picks from the bottom two buckets; select remaining rounds according to the baseline distribution.
     * - CENTER: Reserve multiple focused picks from the single center bucket (odd number of buckets is enforced); select remaining rounds according to the baseline distribution.
     */
    public enum BUCKET_STRATEGY {
        RANDOM,
        EQUAL,
        LEAN_HIGH,
        LEAN_LOW,
        LEAN_CENTER,
        HIGH,
        LOW,
        CENTER
    }

    public BUCKET_STRATEGY chooseStrategyForDate(final LocalDate date) {
        final BUCKET_STRATEGY[] values = BUCKET_STRATEGY.values();
        final Random rng = new Random(date.toEpochDay());
        return values[rng.nextInt(values.length)];
    }

    public List<Integer> planBucketSelection(final BUCKET_STRATEGY strategy, final int bucketCount, final int rounds, final LocalDate date) {
        final ArrayList<Integer> plan = new ArrayList<>(rounds);
        final Random rng = new Random(date.toEpochDay() * 31L + 7L);

        switch (strategy) {
            case EQUAL -> {
                for (int i = 0; i < bucketCount && plan.size() < rounds; i++) plan.add(i);
                // If more rounds than buckets (unlikely), fill uniformly
                while (plan.size() < rounds) plan.add(rng.nextInt(bucketCount));
            }
            case RANDOM -> {
                for (int i = 0; i < rounds; i++) plan.add(rng.nextInt(bucketCount));
            }
            case LEAN_HIGH -> {
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) w[i] = i + 1.0; // higher index = higher weight
                for (int i = 0; i < rounds; i++) plan.add(sampleIndex(w, rng));
            }
            case LEAN_LOW -> {
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) w[i] = (bucketCount - i);
                for (int i = 0; i < rounds; i++) plan.add(sampleIndex(w, rng));
            }
            case LEAN_CENTER -> {
                final int c = bucketCount / 2; // single center due to odd count enforcement
                final double sigma = Math.max(1.0, bucketCount / 5.0); // tighter focus on center
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    final double d = i - c;
                    w[i] = Math.exp(-(d * d) / (2 * sigma * sigma));
                }
                for (int i = 0; i < rounds; i++) plan.add(sampleIndex(w, rng));
            }
            case HIGH -> {
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++)
                    plan.add(bucketCount - 1 - rng.nextInt(Math.min(2, bucketCount))); // top two
                while (plan.size() < rounds) plan.add(rng.nextInt(bucketCount));
            }
            case LOW -> {
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++) plan.add(rng.nextInt(Math.min(2, bucketCount))); // bottom two
                while (plan.size() < rounds) plan.add(rng.nextInt(bucketCount));
            }
            case CENTER -> {
                final int c = bucketCount / 2; // single center due to odd count enforcement
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++) plan.add(c);
                while (plan.size() < rounds) plan.add(rng.nextInt(bucketCount));
            }
        }

        // If we exactly have one per bucket for EQUAL, shuffle to avoid deterministic ordering
        if (strategy == BUCKET_STRATEGY.EQUAL && rounds >= bucketCount) Collections.shuffle(plan, rng);
        return plan;
    }

    public int sampleIndex(final double[] weights, final Random rng) {
        double sum = 0.0;
        for (double w : weights) sum += w;
        double r = rng.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0) return i;
        }
        return weights.length - 1;
    }
}


