package org.steam5.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Steam5 domain-level gauges scraped by Prometheus. Mirrors the data already
 * served by {@code /api/metrics} so dashboards and alerts can rely on
 * Micrometer instead of polling the REST endpoint.
 *
 * <p>A single {@code Snapshot} is recomputed at most once per minute via a
 * Caffeine cache. Without that the 15-second Prometheus scrape would trigger
 * eight {@code COUNT(*)}/{@code MAX(updated_at)} queries against Postgres on
 * every poll.
 */
@Configuration
public class DomainMetricsConfig {

    private static final long SNAPSHOT_TTL_SECONDS = 60L;

    @Bean
    public MeterBinder steam5DomainMetrics(final SteamAppIndexRepository indexRepo,
                                           final SteamAppReviewsRepository reviewsRepo,
                                           final SteamAppDetailRepository detailRepo,
                                           final ReviewGamePickRepository pickRepo) {
        final Cache<String, Snapshot> cache = Caffeine.newBuilder()
                .expireAfterWrite(SNAPSHOT_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(1)
                .build();
        final Supplier<Snapshot> snapshot = () -> cache.get("s",
                k -> loadSnapshot(indexRepo, reviewsRepo, detailRepo, pickRepo));

        return registry -> {
            Gauge.builder("steam5.app.index.size", snapshot, s -> s.get().appIndexSize)
                    .description("Number of apps in the Steam app index")
                    .register(registry);
            Gauge.builder("steam5.app.reviews.coverage.ratio", snapshot, s -> s.get().reviewsCoverage)
                    .description("Fraction of indexed apps with review data (0..1)")
                    .register(registry);
            Gauge.builder("steam5.app.details.coverage.ratio", snapshot, s -> s.get().detailsCoverage)
                    .description("Fraction of indexed apps with detail data (0..1)")
                    .register(registry);
            Gauge.builder("steam5.reviews.oldest.age.seconds", snapshot, s -> s.get().oldestReviewAgeSeconds)
                    .description("Age in seconds of the oldest review_updated_at across all apps")
                    .register(registry);
            Gauge.builder("steam5.reviews.newest.age.seconds", snapshot, s -> s.get().newestReviewAgeSeconds)
                    .description("Age in seconds of the most recent review_updated_at across all apps")
                    .register(registry);
            Gauge.builder("steam5.picks.total", snapshot, s -> s.get().picksTotal)
                    .description("Total review-game picks persisted")
                    .register(registry);
            Gauge.builder("steam5.picks.distinct.days", snapshot, s -> s.get().picksDistinctDays)
                    .description("Distinct days for which review-game picks exist")
                    .register(registry);
            Gauge.builder("steam5.picks.latest.date.epoch.seconds", snapshot, s -> s.get().picksLatestDateEpochSeconds)
                    .description("Unix timestamp (UTC start-of-day) of the most recent review-game pick date")
                    .register(registry);
        };
    }

    private static Snapshot loadSnapshot(final SteamAppIndexRepository indexRepo,
                                         final SteamAppReviewsRepository reviewsRepo,
                                         final SteamAppDetailRepository detailRepo,
                                         final ReviewGamePickRepository pickRepo) {
        final long indexCount = indexRepo.count();
        final long withReviews = indexRepo.countWithReviews();
        final long withDetails = indexRepo.countWithDetails();
        final OffsetDateTime minUpdated = reviewsRepo.minUpdatedAt();
        final OffsetDateTime maxUpdated = reviewsRepo.maxUpdatedAt();
        final long picksTotal = pickRepo.count();
        final long picksDistinctDays = pickRepo.countDistinctPickDates();
        final LocalDate latestPickDate = pickRepo.findLatestPickDate();

        final double nowEpoch = (double) System.currentTimeMillis() / 1000.0;
        final double reviewsCoverage = indexCount == 0 ? 0.0 : (double) withReviews / (double) indexCount;
        final double detailsCoverage = indexCount == 0 ? 0.0 : (double) withDetails / (double) indexCount;
        final double oldestAge = minUpdated == null ? Double.NaN : nowEpoch - minUpdated.toEpochSecond();
        final double newestAge = maxUpdated == null ? Double.NaN : nowEpoch - maxUpdated.toEpochSecond();
        // -1 is a sentinel rather than NaN so the alert can still trigger
        // (Prometheus filters NaN out of `time() - X` comparisons).
        final double latestPickEpoch = latestPickDate == null
                ? -1.0
                : latestPickDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        return new Snapshot(
                (double) indexCount,
                reviewsCoverage,
                detailsCoverage,
                oldestAge,
                newestAge,
                (double) picksTotal,
                (double) picksDistinctDays,
                latestPickEpoch
        );
    }

    private record Snapshot(
            double appIndexSize,
            double reviewsCoverage,
            double detailsCoverage,
            double oldestReviewAgeSeconds,
            double newestReviewAgeSeconds,
            double picksTotal,
            double picksDistinctDays,
            double picksLatestDateEpochSeconds
    ) {
    }
}
