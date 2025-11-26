package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
@Service
public class SteamAppDetailsFetcher implements Fetcher {

    private final SteamAppsConfig properties;
    private final JsonHttpClient jsonHttpClient;
    private final IngestStateRepository ingestStateRepository;
    private final SteamAppIndexRepository appIndexRepository;
    private final SteamAppDetailService service;

    public SteamAppDetailsFetcher(final SteamAppsConfig properties, final JsonHttpClient jsonHttpClient, final IngestStateRepository ingestStateRepository, final SteamAppIndexRepository appIndexRepository, final SteamAppDetailService service) {
        this.properties = properties;
        this.jsonHttpClient = jsonHttpClient;
        this.ingestStateRepository = ingestStateRepository;
        this.appIndexRepository = appIndexRepository;
        this.service = service;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        final long lastAppId = ingestStateRepository.findById("steam_app_details").map(IngestState::getLastAppId).orElse(0L);
        log.info("Starting details ingestion from appId > {}", lastAppId);

        long processed = 0L;
        Long cursor = lastAppId;
        final int pageSize = 500; // single HTTP call per app, moderate batch size
        boolean more = true;
        while (more) {
            final Page<SteamAppIndex> page = appIndexRepository.findByAppIdGreaterThan(cursor, PageRequest.of(0, pageSize, Sort.by("appId").ascending()));
            if (page.isEmpty()) {
                break;
            }
            for (SteamAppIndex idx : page) {
                final Long appId = idx.getAppId();
                if (appId == null) continue;
                try {
                    fetchForAppId(appId);
                } catch (Exception e) {
                    // bail immediately on any HTTP error such as 429
                    throw e;
                } finally {
                    // advance cursor regardless of outcome to avoid getting stuck
                    ingestStateRepository.upsert("steam_app_details", appId, OffsetDateTime.now());
                }
                processed++;
                cursor = appId;
            }
            more = page.hasNext();
        }

        log.info("Details ingestion finished. processed={} starting_after={}", processed, lastAppId);
    }

    public boolean fetchForAppId(final Long appId) throws IOException {
        final String url = UriComponentsBuilder.fromUriString("https://store.steampowered.com/api/appdetails")
                .queryParam("appids", appId)
                .queryParam("key", properties.getApiKey())
                .build(true)
                .toUriString();

        final JsonNode root = jsonHttpClient.getJson(url);
        final JsonNode appNode = root.path(String.valueOf(appId));
        if (appNode.path("success").asInt(0) != 1) {
            log.debug("Details API returned non-success for appId {}", appId);
            return false;
        }

        final JsonNode data = appNode.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            log.debug("Details API missing data for appId {}", appId);
            return false;
        }

        service.upsertFromJson(appId, data);
        return true;
    }
}
