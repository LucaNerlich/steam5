package org.steam5.game.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.SteamAppReviews;
import org.steam5.http.SteamApiException;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.service.SteamAppReviewsFetcher;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewPickGenerator {

    public static final int MIN_BUCKET_BOUND = 1;

    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppReviewsFetcher reviewsFetcher;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final ExcludedAppRepository excludedAppRepository;
    private final ReviewGameConfig config;
    private final ApplicationEventPublisher eventPublisher;

    public List<ReviewGamePick> createPicks(final LocalDate today) {
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500
                ? LocalDate.of(1970, MIN_BUCKET_BOUND, MIN_BUCKET_BOUND)
                : today.minusDays(doNotRepeatDays);

        final List<ReviewGamePick> picks = new ArrayList<>(5);
        final Set<Long> chosenIds = new HashSet<>();

        final List<int[]> bucketRanges = buildBucketRanges();
        final ReviewBucketStrategy strategy = chooseStrategyForDate(today);
        log.info("Bucket strategy for {}: {}", today, strategy);

        final List<Integer> bucketOrder = planBucketSelection(strategy, bucketRanges.size(), bucketRanges.size(), today);
        final List<String> labels = getBucketLabels();

        int round = 1;
        for (Integer bucketIndex : bucketOrder) {
            final int[] range = bucketRanges.get(bucketIndex);
            boolean added = false;
            final List<Long> candidates = (range[1] == Integer.MAX_VALUE)
                    ? reviewsRepository.findRandomGte(excludeSince, range[0], PageRequest.of(0, 8))
                    : reviewsRepository.findRandomBetween(excludeSince, range[0], range[1], PageRequest.of(0, 8));

            for (Long id : candidates) {
                if (!chosenIds.contains(id) && validateAppOrExclude(id)) {
                    chosenIds.add(id);
                    picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                    log.info("Round {}: bucket {} (range {}-{}) -> picked appId {}", round,
                            (labels.isEmpty() ? bucketIndex : labels.get(bucketIndex)),
                            range[0], range[1] == Integer.MAX_VALUE ? "∞" : String.valueOf(range[1]), id);
                    added = true;
                    break;
                }
            }

            if (!added) {
                final List<Long> anyIds = reviewsRepository.findRandomAnyAppIds(excludeSince, PageRequest.of(0, 10));
                for (Long id : anyIds) {
                    if (!chosenIds.contains(id) && validateAppOrExclude(id)) {
                        chosenIds.add(id);
                        picks.add(new ReviewGamePick(null, today, id, OffsetDateTime.now()));
                        log.info("Round {}: bucket {} fallback ANY -> picked appId {}", round,
                                (labels.isEmpty() ? bucketIndex : labels.get(bucketIndex)), id);
                        break;
                    }
                }
            }
            round++;
        }

        Collections.shuffle(picks);
        return picks;
    }

    public void enrichPickedApp(final ReviewGamePick pick) {
        try {
            final int days = config.getMinReviewsFreshDays();
            if (days > 0) {
                final OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
                final SteamAppReviews existingReviews = reviewsRepository.findById(pick.getAppId()).orElse(null);
                final boolean needsRefresh = existingReviews == null
                        || existingReviews.getUpdatedAt() == null
                        || existingReviews.getUpdatedAt().isBefore(cutoff);
                if (needsRefresh) {
                    log.info("Refreshing reviews for picked appId {} (cutoff={}, existingAt={})",
                            pick.getAppId(), cutoff, existingReviews != null ? existingReviews.getUpdatedAt() : null);
                    reviewsFetcher.fetchForAppId(pick.getAppId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to conditionally refresh reviews for picked appId {}", pick.getAppId(), e);
        }

        try {
            detailsFetcher.fetchForAppId(pick.getAppId());
        } catch (Exception e) {
            log.warn("Failed to refresh details for picked appId {}", pick.getAppId(), e);
        }

        eventPublisher.publishEvent(new BlurhashEncodeRequested(pick.getAppId(), null, BlurhashEnqueueListener.Type.SCREENSHOT));
    }

    private List<int[]> buildBucketRanges() {
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

        final int bucketCount = bucketRanges.size();
        if (bucketCount < 5 || (bucketCount > 5 && bucketCount % 2 == 0)) {
            throw new IllegalStateException("Invalid bucket configuration: " + bucketCount
                    + " buckets. Must be at least 5 and odd when > 5.");
        }
        return bucketRanges;
    }

    private boolean validateAppOrExclude(final Long appId) {
        try {
            boolean ok = detailsFetcher.fetchForAppId(appId);
            if (!ok) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "details fetch failed or success=false", OffsetDateTime.now()));
            }
            return ok;
        } catch (SteamApiException sae) {
            if (sae.getStatusCode() == 429) {
                log.warn("Steam API rate limited (429) while validating appId {}. Aborting pick generation without exclusion.", appId);
                throw new RuntimeException(sae);
            }
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "details fetch error: HTTP " + sae.getStatusCode(), OffsetDateTime.now()));
            return false;
        } catch (Exception e) {
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "details fetch error: " + e.getMessage(), OffsetDateTime.now()));
            return false;
        }
    }

    public List<String> getBucketLabels() {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) {
            return List.of();
        }

        final ArrayList<String> labels = new ArrayList<>(bounds.size() + MIN_BUCKET_BOUND);
        int prev = MIN_BUCKET_BOUND;
        for (Integer b : bounds) {
            labels.add((prev == MIN_BUCKET_BOUND ? "1-" + b : (prev + MIN_BUCKET_BOUND) + "-" + b));
            prev = b;
        }
        labels.add(bounds.getLast() + "+");
        return labels;
    }

    public ReviewBucketStrategy chooseStrategyForDate(final LocalDate date) {
        final ReviewBucketStrategy[] values = ReviewBucketStrategy.values();
        final Random rng = new Random(mix64(date.toEpochDay()));
        return values[rng.nextInt(values.length)];
    }

    public int sampleIndex(final double[] weights, final Random rng) {
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        double r = rng.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    public List<Integer> planBucketSelection(final ReviewBucketStrategy strategy,
                                             final int bucketCount,
                                             final int rounds,
                                             final LocalDate date) {
        final ArrayList<Integer> plan = new ArrayList<>(rounds);
        final Random rng = new Random(mix64(date.toEpochDay() * 31L + 7L));

        switch (strategy) {
            case EQUAL -> {
                for (int i = 0; i < bucketCount && plan.size() < rounds; i++) {
                    plan.add(i);
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case RANDOM -> {
                for (int i = 0; i < rounds; i++) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case LEAN_HIGH -> {
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    w[i] = i + 1.0;
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(w, rng));
                }
            }
            case LEAN_LOW -> {
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    w[i] = (bucketCount - i);
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(w, rng));
                }
            }
            case LEAN_CENTER -> {
                final int c = bucketCount / 2;
                final double sigma = Math.max(1.0, bucketCount / 5.0);
                final double[] w = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    final double d = i - c;
                    w[i] = Math.exp(-(d * d) / (2 * sigma * sigma));
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(w, rng));
                }
            }
            case HIGH -> {
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++) {
                    plan.add(bucketCount - 1 - rng.nextInt(Math.min(2, bucketCount)));
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case LOW -> {
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++) {
                    plan.add(rng.nextInt(Math.min(2, bucketCount)));
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case CENTER -> {
                final int c = bucketCount / 2;
                final int k = Math.min(2, rounds);
                for (int i = 0; i < k; i++) {
                    plan.add(c);
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
        }

        if (strategy == ReviewBucketStrategy.EQUAL && rounds >= bucketCount) {
            Collections.shuffle(plan, rng);
        }
        return plan;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
