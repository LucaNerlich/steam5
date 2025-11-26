package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
@Service
public class SteamAppListFetcher implements Fetcher {

    private final SteamAppsConfig properties;
    private final JsonHttpClient jsonHttpClient;
    private final SteamAppIndexRepository appIndexRepository;
    private final IngestStateRepository ingestStateRepository;

    public SteamAppListFetcher(SteamAppsConfig properties,
                               SteamAppIndexRepository appIndexRepository,
                               IngestStateRepository ingestStateRepository,
                               JsonHttpClient jsonHttpClient) {
        this.properties = properties;
        this.appIndexRepository = appIndexRepository;
        this.ingestStateRepository = ingestStateRepository;
        this.jsonHttpClient = jsonHttpClient;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("steam.apps.apiKey must be configured");
        }

        boolean haveMore = true;
        long lastAppId = ingestStateRepository.findById("steam_app_list").map(IngestState::getLastAppId).orElse(0L);
        int page = 0;
        long totalApps = 0;

        while (haveMore) {
            page++;
            final String url = buildUrl(lastAppId);
            log.info("Fetching Steam app list page {} with last_appid={} ...", page, lastAppId);

            final JsonNode root = jsonHttpClient.getJson(url);
            final JsonNode response = root.path("response");
            final JsonNode apps = response.path("apps");

            int batchCount = 0;
            if (apps.isArray()) {
                for (JsonNode app : apps) {
                    final long appId = app.path("appid").asLong();
                    final String name = app.path("name").asText("");
                    if (appId <= 0 || name.isEmpty()) {
                        continue;
                    }
                    appIndexRepository.save(new SteamAppIndex(appId, name));
                    batchCount++;
                }
                totalApps += apps.size();
            }

            haveMore = response.path("have_more_results").asBoolean(false);
            lastAppId = response.path("last_appid").asLong(lastAppId);

            // Persist cursor after each page
            ingestStateRepository.upsert("steam_app_list", lastAppId, OffsetDateTime.now());

            log.info("Persisted page {} (apps processed: {} of {}), have_more_results={}, next last_appid={}",
                    page, batchCount, apps.isArray() ? apps.size() : -1, haveMore, lastAppId);
        }

        log.info("Steam app list ingestion finished. pages={}, total_apps_seen={}", page, totalApps);
    }

    private String buildUrl(long lastAppId) {
        final UriComponentsBuilder b = UriComponentsBuilder.fromUriString(properties.getApiUrl())
                .queryParam("key", properties.getApiKey())
                .queryParam("max_results", 50000)
                .queryParam("include_games", properties.isIncludeGames());

        if (lastAppId > 0) {
            b.queryParam("last_appid", lastAppId);
        }

        return b.build(true).toUriString();
    }
}


