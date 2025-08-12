package org.steam5.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.SteamAppReviews;
import org.steam5.http.SteamHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
public class SteamAppReviewsFetcher implements Fetcher {

    private static final Logger log = LoggerFactory.getLogger(SteamAppReviewsFetcher.class);

    private final ObjectMapper objectMapper;
    private final SteamAppsConfig properties;
    private final SteamHttpClient http;
    private final SteamAppIndexRepository appIndexRepository;
    private final SteamAppReviewsRepository reviewsRepository;
    private final IngestStateRepository ingestStateRepository;

    public SteamAppReviewsFetcher(SteamAppsConfig properties,
                                  ObjectMapper objectMapper,
                                  SteamHttpClient http,
                                  SteamAppIndexRepository appIndexRepository,
                                  SteamAppReviewsRepository reviewsRepository,
                                  IngestStateRepository ingestStateRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = http;
        this.appIndexRepository = appIndexRepository;
        this.reviewsRepository = reviewsRepository;
        this.ingestStateRepository = ingestStateRepository;
    }

    @Override
    public void ingest() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        long lastAppId = ingestStateRepository.findById("steam_app_reviews").map(IngestState::getLastAppId).orElse(0L);
        log.info("Starting reviews ingestion from appId > {}", lastAppId);

        long processed = 0L;
        Long cursor = lastAppId;
        int pageSize = 5000; // large batches; single HTTP call per app
        boolean more = true;
        while (more) {
            var page = appIndexRepository.findByAppIdGreaterThan(cursor, PageRequest.of(0, pageSize, Sort.by("appId").ascending()));
            if (page.isEmpty()) {
                break;
            }
            for (SteamAppIndex idx : page) {
                final Long appId = idx.getAppId();
                if (appId == null) continue;
                try {
                    processSingleAppId(appId);
                    ingestStateRepository.upsert("steam_app_reviews", appId, OffsetDateTime.now());
                } catch (Exception e) {
                    log.warn("Failed to fetch reviews for appId {}: {}", appId, e.getMessage());
                }
                processed++;
                cursor = appId;
            }
            more = page.hasNext();
        }

        log.info("Reviews ingestion finished. processed={} starting_after={}", processed, lastAppId);
    }

    private void processSingleAppId(Long appId) throws IOException {
        final String url = UriComponentsBuilder.fromUriString("https://store.steampowered.com/appreviews/" + appId)
                .queryParam("json", 1)
                .queryParam("num_per_page", 0) //  don't fetch actual reviews details
                .queryParam("language", "all")
                .queryParam("purchase_type", "all")
                .queryParam("key", properties.getApiKey())
                .build(true)
                .toUriString();

        final String body = http.get(url);
        if (body == null) {
            throw new IOException("Empty response from reviews API for appId=" + appId);
        }

        final JsonNode root = objectMapper.readTree(body);
        if (root.path("success").asInt(0) != 1) {
            log.debug("Reviews API returned non-success for appId {}: {}", appId, body);
        }

        final JsonNode summary = root.path("query_summary");
        int totalPositive = summary.path("total_positive").asInt(0);
        int totalNegative = summary.path("total_negative").asInt(0);

        SteamAppReviews entity = new SteamAppReviews(appId, totalPositive, totalNegative, OffsetDateTime.now());
        reviewsRepository.save(entity);
    }
}


