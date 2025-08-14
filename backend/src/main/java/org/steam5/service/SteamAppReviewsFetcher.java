package org.steam5.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.SteamAppReviews;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;

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

    public SteamAppReviewsFetcher(SteamAppsConfig properties,
                                  JsonHttpClient jsonHttpClient,
                                  SteamAppIndexRepository appIndexRepository,
                                  SteamAppReviewsRepository reviewsRepository,
                                  IngestStateRepository ingestStateRepository) {
        this.properties = properties;
        this.jsonHttpClient = jsonHttpClient;
        this.appIndexRepository = appIndexRepository;
        this.reviewsRepository = reviewsRepository;
        this.ingestStateRepository = ingestStateRepository;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        final long lastAppId = ingestStateRepository.findById("steam_app_reviews").map(IngestState::getLastAppId).orElse(0L);
        log.info("Starting reviews ingestion from appId > {}", lastAppId);

        long processed = 0L;
        Long cursor = lastAppId;
        final int pageSize = 1000; // large batches; single HTTP call per app
        boolean more = true;
        while (more) {
            final Page<SteamAppIndex> page = appIndexRepository.findByAppIdGreaterThan(cursor, PageRequest.of(0, pageSize, Sort.by("appId").ascending()));
            if (page.isEmpty()) {
                break;
            }
            for (SteamAppIndex idx : page) {
                final Long appId = idx.getAppId();
                if (appId == null) continue;
                fetchForAppId(appId);
                ingestStateRepository.upsert("steam_app_reviews", appId, OffsetDateTime.now());
                processed++;
                cursor = appId;
            }
            more = page.hasNext();
        }

        log.info("Reviews ingestion finished. processed={} starting_after={}", processed, lastAppId);
    }

    public void fetchForAppId(Long appId) throws IOException {
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
            return;
        }

        final JsonNode summary = root.path("query_summary");
        int totalPositive = summary.path("total_positive").asInt(0);
        int totalNegative = summary.path("total_negative").asInt(0);

        SteamAppReviews entity = new SteamAppReviews(appId, totalPositive, totalNegative, OffsetDateTime.now());
        reviewsRepository.save(entity);
    }
}


