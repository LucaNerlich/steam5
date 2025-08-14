package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.details.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/metrics")
@Validated
public class MetricsController {

    private final SteamAppIndexRepository indexRepository;
    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppDetailRepository detailRepository;
    private final DeveloperRepository developerRepository;
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final PublisherRepository publisherRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ReviewGamePickRepository pickRepository;
    private final IngestStateRepository ingestStateRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> indexJson() {
        final Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/metrics");
        links.put("counts", "/api/metrics/counts");
        links.put("coverage", "/api/metrics/coverage");
        links.put("thresholds", "/api/metrics/thresholds");
        links.put("detailsBreakdown", "/api/metrics/details/breakdown");
        links.put("picksSummary", "/api/metrics/picks/summary");
        return ResponseEntity.ok(links);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> indexHtml() {
        final String html = """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Metrics Index</title>
                  <style>
                    body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;padding:20px;line-height:1.5}
                    h1{margin-top:0}
                    ul{padding-left:18px}
                    code{background:#f4f4f4;padding:2px 6px;border-radius:4px}
                  </style>
                </head>
                <body>
                  <h1>Metrics Index</h1>
                  <ul>
                    <li><a href=\"/api/metrics\">self</a> <code>GET /api/metrics</code></li>
                    <li><a href=\"/api/metrics/counts\">counts</a> <code>GET /api/metrics/counts</code></li>
                    <li><a href=\"/api/metrics/coverage\">coverage</a> <code>GET /api/metrics/coverage</code></li>
                    <li><a href=\"/api/metrics/thresholds\">thresholds</a> <code>GET /api/metrics/thresholds</code></li>
                    <li><a href=\"/api/metrics/details/breakdown\">detailsBreakdown</a> <code>GET /api/metrics/details/breakdown</code></li>
                    <li><a href=\"/api/metrics/picks/summary\">picksSummary</a> <code>GET /api/metrics/picks/summary</code></li>
                  </ul>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/thresholds")
    @Cacheable(value = "one-day", key = "'thresholds'", unless = "#result == null")
    public ResponseEntity<SteamAppReviewsRepository.ReviewThresholds> getThresholds() {
        final SteamAppReviewsRepository.ReviewThresholds thresholds = reviewsRepository.findPercentileThresholds();
        return ResponseEntity.ok(thresholds);
    }

    @GetMapping("/counts")
    @Cacheable(value = "one-hour", key = "'counts'", unless = "#result == null")
    public ResponseEntity<EntityCounts> getEntityCounts() {
        final EntityCounts counts = new EntityCounts(
                indexRepository.count(),
                reviewsRepository.count(),
                detailRepository.count(),
                developerRepository.count(),
                genreRepository.count(),
                movieRepository.count(),
                publisherRepository.count(),
                screenshotRepository.count(),
                pickRepository.count(),
                ingestStateRepository.count()
        );
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/coverage")
    @Cacheable(value = "one-hour", key = "'coverage'", unless = "#result == null")
    public ResponseEntity<Coverage> getCoverage() {
        final long indexCount = indexRepository.count();
        final long withReviews = indexRepository.countWithReviews();
        final long withDetails = indexRepository.countWithDetails();
        final long missingReviews = Math.max(0, indexCount - withReviews);
        final long missingDetails = Math.max(0, indexCount - withDetails);
        final Coverage coverage = new Coverage(
                indexCount,
                withReviews,
                withDetails,
                missingReviews,
                missingDetails,
                indexCount == 0 ? 0.0 : (withReviews * 100.0) / indexCount,
                indexCount == 0 ? 0.0 : (withDetails * 100.0) / indexCount
        );
        return ResponseEntity.ok(coverage);
    }

    @GetMapping("/reviews/summary")
    @Cacheable(value = "one-hour", key = "'reviews-summary'", unless = "#result == null")
    public ResponseEntity<ReviewsSummary> getReviewsSummary() {
        final long appsWithReviews = reviewsRepository.count();
        final long totalReviews = reviewsRepository.sumTotalReviews();
        final var minUpdated = reviewsRepository.minUpdatedAt();
        final var maxUpdated = reviewsRepository.maxUpdatedAt();
        final double avgReviewsPerApp = appsWithReviews == 0 ? 0.0 : (double) totalReviews / appsWithReviews;
        final ReviewsSummary summary = new ReviewsSummary(appsWithReviews, totalReviews, avgReviewsPerApp, minUpdated, maxUpdated);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/details/breakdown")
    @Cacheable(value = "one-hour", key = "'details-breakdown'", unless = "#result == null")
    public ResponseEntity<DetailsBreakdown> getDetailsBreakdown() {
        final long detailsCount = detailRepository.count();
        final DetailsBreakdown breakdown = new DetailsBreakdown(
                detailsCount,
                detailRepository.countByIsFreeTrue(),
                detailRepository.countByIsFreeFalse(),
                detailRepository.countByIsWindowsTrue(),
                detailRepository.countByIsMacTrue(),
                detailRepository.countByIsLinuxTrue()
        );
        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/picks/summary")
    @Cacheable(value = "one-hour", key = "'picks-summary'", unless = "#result == null")
    public ResponseEntity<PicksSummary> getPicksSummary() {
        final long totalPicks = pickRepository.count();
        final long distinctDays = pickRepository.countDistinctPickDates();
        final var latestDay = pickRepository.findLatestPickDate();
        return ResponseEntity.ok(new PicksSummary(totalPicks, distinctDays, latestDay));
    }

    public record EntityCounts(
            long steamAppIndexCount,
            long steamAppReviewsCount,
            long steamAppDetailCount,
            long developerCount,
            long genreCount,
            long movieCount,
            long publisherCount,
            long screenshotCount,
            long reviewGamePickCount,
            long ingestStateCount
    ) {
    }

    public record Coverage(
            long steamAppIndexCount,
            long indexWithReviewsCount,
            long indexWithDetailsCount,
            long indexMissingReviewsCount,
            long indexMissingDetailsCount,
            double reviewsCoveragePercent,
            double detailsCoveragePercent
    ) {
    }

    public record ReviewsSummary(
            long appsWithReviewsCount,
            long totalReviewsCount,
            double averageReviewsPerApp,
            java.time.OffsetDateTime oldestReviewUpdate,
            java.time.OffsetDateTime newestReviewUpdate
    ) {
    }

    public record DetailsBreakdown(
            long steamAppDetailCount,
            long freeCount,
            long paidCount,
            long windowsCount,
            long macCount,
            long linuxCount
    ) {
    }

    public record PicksSummary(
            long totalPicks,
            long distinctPickDays,
            java.time.LocalDate latestPickDate
    ) {
    }
}

