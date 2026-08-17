package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.JobsConfig;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.SteamAppReviews;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
@Service
public class SteamAppReviewsFetcher implements Fetcher {

    private final SteamAppsConfig properties;
    private final JsonHttpClient jsonHttpClient;
    private final SteamAppIndexRepository appIndexRepository;
    private final SteamAppReviewsRepository reviewsRepository;
    private final IngestStateRepository ingestStateRepository;
    private final CacheManager cacheManager;
    private final JobsConfig jobsConfig;

    public SteamAppReviewsFetcher(SteamAppsConfig properties,
                                  JsonHttpClient jsonHttpClient,
                                  SteamAppIndexRepository appIndexRepository,
                                  SteamAppReviewsRepository reviewsRepository,
                                  IngestStateRepository ingestStateRepository,
                                  CacheManager cacheManager,
                                  JobsConfig jobsConfig) {
        this.properties = properties;
        this.jsonHttpClient = jsonHttpClient;
        this.appIndexRepository = appIndexRepository;
        this.reviewsRepository = reviewsRepository;
        this.ingestStateRepository = ingestStateRepository;
        this.cacheManager = cacheManager;
        this.jobsConfig = jobsConfig;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        final long lastAppId = ingestStateRepository.findById("steam_app_reviews").map(IngestState::getLastAppId).orElse(0L);
        final int batchLimit = Math.max(1, jobsConfig.getSteamAppReviews().getBatchLimit());
        log.info("Starting reviews ingestion from appId > {} (batchLimit={})", lastAppId, batchLimit);

        long processed = 0L;
        Long cursor = lastAppId;
        final int pageSize = 1000; // large batches; single HTTP call per app
        boolean more = true;
        while (more && processed < batchLimit) {
            final Page<SteamAppIndex> page = appIndexRepository.findByAppIdGreaterThan(cursor, PageRequest.of(0, pageSize, Sort.by("appId").ascending()));
            if (page.isEmpty()) {
                break;
            }
            for (SteamAppIndex idx : page) {
                if (processed >= batchLimit) {
                    break;
                }
                final Long appId = idx.getAppId();
                if (appId == null) continue;
                // Bulk path: skip the aggregate cache clear per app (it would
                // thrash the whole review-game cache up to batchLimit times per
                // run); the cache is cleared once at the end of the job below.
                fetchForAppId(appId, false);
                ingestStateRepository.upsert("steam_app_reviews", appId, OffsetDateTime.now());
                processed++;
                cursor = appId;
            }
            more = page.hasNext() && processed < batchLimit;
        }

        if (processed > 0) {
            final var reviewGame = cacheManager.getCache("review-game");
            if (reviewGame != null) {
                reviewGame.clear();
                log.info("Cleared review-game cache after bulk reviews ingestion of {} apps", processed);
            }
        }

        log.info("Reviews ingestion finished. processed={} batchLimit={} starting_after={}", processed, batchLimit, lastAppId);
    }

    public boolean fetchForAppId(Long appId) throws IOException {
        return fetchForAppId(appId, true);
    }

    /**
     * @param clearReviewGameAggregates when true, clears the whole {@code review-game} cache after
     *                                  saving (safe for single-app updates). Nightly bulk refresh
     *                                  should pass false and clear once at the end of the job to
     *                                  avoid thrashing caches under memory pressure.
     * @return true if the API response was successful and data was persisted, false otherwise
     */
    public boolean fetchForAppId(Long appId, boolean clearReviewGameAggregates) throws IOException {
        final String url = UriComponentsBuilder.fromUriString("https://store.steampowered.com/appreviews/" + appId)
                .queryParam("json", 1)
                .queryParam("num_per_page", 0) //  don't fetch actual review details
                .queryParam("language", "all")
                .queryParam("purchase_type", "all")
                .queryParam("key", properties.getApiKey())
                .build(true)
                .toUriString();

        final JsonNode root = jsonHttpClient.getJson(url);
        if (root.path("success").asInt(0) != 1) {
            log.error("Reviews API returned non-success for appId {}", appId);
            return false;
        }

        final JsonNode summary = root.path("query_summary");
        int totalPositive = summary.path("total_positive").asInt(0);
        int totalNegative = summary.path("total_negative").asInt(0);

        SteamAppReviews entity = new SteamAppReviews(appId, totalPositive, totalNegative, OffsetDateTime.now());
        reviewsRepository.save(entity);

        final var reviewGame = cacheManager.getCache("review-game");
        if (reviewGame != null) {
            reviewGame.evict(appId + "review-count");
            if (clearReviewGameAggregates) {
                // today/picks can change derived buckets if numbers shift
                reviewGame.clear();
            }
        }
        return true;
    }
}


