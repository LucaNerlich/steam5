package org.steam5.game.year;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.game.review.ReviewBucketStrategy;
import org.steam5.http.SteamApiException;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.util.ReleaseDateParser;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class YearPickGenerator {

    private static final int MIN_RECENT_RELEASE_DAYS = 7;

    private final SteamAppDetailRepository detailRepository;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final ExcludedAppRepository excludedAppRepository;
    private final YearGameConfig config;
    private final ApplicationEventPublisher eventPublisher;

    public List<YearGamePick> createPicks(final LocalDate today) {
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500
                ? LocalDate.of(1970, YearGuessEvaluator.MIN_YEAR_BOUND, YearGuessEvaluator.MIN_YEAR_BOUND)
                : today.minusDays(doNotRepeatDays);

        final int rounds = Math.max(1, config.getRoundsPerDay());
        final List<YearGamePick> picks = new ArrayList<>(rounds);
        final Set<Long> chosenIds = new HashSet<>();

        final List<int[]> bucketRanges = buildBucketRanges();
        final ReviewBucketStrategy strategy = chooseStrategyForDate(today);
        log.info("Year bucket strategy for {}: {}", today, strategy);

        final List<Integer> bucketOrder = planBucketSelection(strategy, bucketRanges.size(), rounds, today);
        final List<String> labels = getBucketLabels();

        int round = 1;
        for (Integer bucketIndex : bucketOrder) {
            final int[] range = bucketRanges.get(bucketIndex);
            boolean added = false;
            final List<Long> candidates = range[1] == Integer.MAX_VALUE
                    ? detailRepository.findRandomByReleaseYearGte(excludeSince, range[0], PageRequest.of(0, 8))
                    : detailRepository.findRandomByReleaseYearBetween(excludeSince, range[0], range[1], PageRequest.of(0, 8));

            for (Long id : candidates) {
                if (!chosenIds.contains(id) && validateAppOrExclude(id)) {
                    chosenIds.add(id);
                    picks.add(new YearGamePick(null, today, id, OffsetDateTime.now()));
                    log.info("Year round {}: bucket {} (range {}-{}) -> picked appId {}", round,
                            labels.isEmpty() ? bucketIndex : labels.get(bucketIndex),
                            range[0], range[1] == Integer.MAX_VALUE ? "∞" : String.valueOf(range[1]), id);
                    added = true;
                    break;
                }
            }

            if (!added) {
                final List<Long> anyIds = detailRepository.findRandomAnyReleaseYear(excludeSince, PageRequest.of(0, 10));
                for (Long id : anyIds) {
                    if (!chosenIds.contains(id) && validateAppOrExclude(id)) {
                        chosenIds.add(id);
                        picks.add(new YearGamePick(null, today, id, OffsetDateTime.now()));
                        log.info("Year round {}: bucket {} fallback ANY -> picked appId {}", round,
                                labels.isEmpty() ? bucketIndex : labels.get(bucketIndex), id);
                        break;
                    }
                }
            }
            round++;
        }

        Collections.shuffle(picks);
        return picks;
    }

    public void enrichPickedApp(final YearGamePick pick) {
        try {
            detailsFetcher.fetchForAppId(pick.getAppId());
        } catch (Exception e) {
            log.warn("Failed to refresh details for picked year-game appId {}", pick.getAppId(), e);
        }

        eventPublisher.publishEvent(new BlurhashEncodeRequested(pick.getAppId(), null, BlurhashEnqueueListener.Type.SCREENSHOT));
    }

    public List<String> getBucketLabels() {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) {
            return List.of();
        }

        final ArrayList<String> labels = new ArrayList<>(bounds.size() + 1);
        int previous = YearGuessEvaluator.MIN_YEAR_BOUND;
        for (Integer bound : bounds) {
            labels.add(previous == YearGuessEvaluator.MIN_YEAR_BOUND
                    ? YearGuessEvaluator.MIN_YEAR_BOUND + "-" + bound
                    : (previous + 1) + "-" + bound);
            previous = bound;
        }
        labels.add(bounds.getLast() + "+");
        return labels;
    }

    public ReviewBucketStrategy chooseStrategyForDate(final LocalDate date) {
        final ReviewBucketStrategy[] values = ReviewBucketStrategy.values();
        final Random rng = new Random(mix64(date.toEpochDay()));
        return values[rng.nextInt(values.length)];
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
                final double[] weights = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    weights[i] = i + 1.0;
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(weights, rng));
                }
            }
            case LEAN_LOW -> {
                final double[] weights = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    weights[i] = bucketCount - i;
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(weights, rng));
                }
            }
            case LEAN_CENTER -> {
                final int center = bucketCount / 2;
                final double sigma = Math.max(1.0, bucketCount / 5.0);
                final double[] weights = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    final double distance = i - center;
                    weights[i] = Math.exp(-(distance * distance) / (2 * sigma * sigma));
                }
                for (int i = 0; i < rounds; i++) {
                    plan.add(sampleIndex(weights, rng));
                }
            }
            case HIGH -> {
                final int guaranteed = Math.min(2, rounds);
                for (int i = 0; i < guaranteed; i++) {
                    plan.add(bucketCount - 1 - rng.nextInt(Math.min(2, bucketCount)));
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case LOW -> {
                final int guaranteed = Math.min(2, rounds);
                for (int i = 0; i < guaranteed; i++) {
                    plan.add(rng.nextInt(Math.min(2, bucketCount)));
                }
                while (plan.size() < rounds) {
                    plan.add(rng.nextInt(bucketCount));
                }
            }
            case CENTER -> {
                final int center = bucketCount / 2;
                final int guaranteed = Math.min(2, rounds);
                for (int i = 0; i < guaranteed; i++) {
                    plan.add(center);
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

    private List<int[]> buildBucketRanges() {
        final List<Integer> bounds = config.getBucketBoundaries();
        final List<int[]> bucketRanges = new ArrayList<>();
        if (bounds != null && !bounds.isEmpty()) {
            int previous = YearGuessEvaluator.MIN_YEAR_BOUND;
            for (int bound : bounds) {
                bucketRanges.add(new int[]{previous, bound});
                previous = bound + 1;
            }
            bucketRanges.add(new int[]{bounds.getLast() + 1, Integer.MAX_VALUE});
        } else {
            bucketRanges.add(new int[]{1970, 1999});
            bucketRanges.add(new int[]{2000, 2009});
            bucketRanges.add(new int[]{2010, 2019});
            bucketRanges.add(new int[]{2020, Integer.MAX_VALUE});
        }

        if (bucketRanges.size() < 2) {
            throw new IllegalStateException("Invalid year bucket configuration: need at least 2 buckets");
        }
        return bucketRanges;
    }

    private boolean validateAppOrExclude(final Long appId) {
        try {
            final boolean fetched = detailsFetcher.fetchForAppId(appId);
            if (!fetched) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game details fetch failed or success=false", OffsetDateTime.now()));
                return false;
            }

            final Optional<SteamAppDetail> detail = detailRepository.findByAppId(appId);
            if (detail.isEmpty()) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game missing details after fetch", OffsetDateTime.now()));
                return false;
            }

            final String releaseDate = detail.get().getReleaseDate();
            if (ReleaseDateParser.isComingSoonOrUnknown(releaseDate)
                    || ReleaseDateParser.parseYear(releaseDate).isEmpty()) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game unparseable release date: " + releaseDate, OffsetDateTime.now()));
                return false;
            }

            if (ReleaseDateParser.isReleasedWithinDays(releaseDate, MIN_RECENT_RELEASE_DAYS)) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game released within last " + MIN_RECENT_RELEASE_DAYS + " days", OffsetDateTime.now()));
                return false;
            }

            return true;
        } catch (SteamApiException sae) {
            if (sae.getStatusCode() == 429) {
                log.warn("Steam API rate limited (429) while validating year-game appId {}. Aborting pick generation.", appId);
                throw new RuntimeException(sae);
            }
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "year-game details fetch error: HTTP " + sae.getStatusCode(), OffsetDateTime.now()));
            return false;
        } catch (Exception e) {
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "year-game details fetch error: " + e.getMessage(), OffsetDateTime.now()));
            return false;
        }
    }

    public int sampleIndex(final double[] weights, final Random rng) {
        double sum = 0.0;
        for (double weight : weights) {
            sum += weight;
        }
        double remaining = rng.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            remaining -= weights[i];
            if (remaining <= 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
