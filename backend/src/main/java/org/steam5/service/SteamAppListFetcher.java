package org.steam5.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.SteamAppIndex;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
public class SteamAppListFetcher {

    private static final Logger log = LoggerFactory.getLogger(SteamAppListFetcher.class);

    private final SteamAppsConfig properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SteamAppIndexRepository appIndexRepository;
    private final SteamRateLimiter rateLimiter;
    private final IngestStateRepository ingestStateRepository;

    public SteamAppListFetcher(SteamAppsConfig properties,
                               ObjectMapper objectMapper,
                               SteamAppIndexRepository appIndexRepository,
                               SteamRateLimiter rateLimiter,
                               IngestStateRepository ingestStateRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.appIndexRepository = appIndexRepository;
        this.rateLimiter = rateLimiter;
        this.ingestStateRepository = ingestStateRepository;

        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getHttpTimeoutMs());
        requestFactory.setReadTimeout(properties.getHttpTimeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public void ingestFullListToDatabase() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("steam.apps.apiKey must be configured");
        }

        boolean haveMore = true;
        long lastAppId = ingestStateRepository.findById("steam_app_list").map(IngestState::getLastAppId).orElse(0L);
        int page = 0;
        long totalApps = 0;

        while (haveMore) {
            page++;
            String url = buildUrl(lastAppId);
            log.info("Fetching Steam app list page {} with last_appid={} ...", page, lastAppId);

            rateLimiter.acquirePermit();
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                throw new IOException("Empty response from Steam API");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root.path("response");
            JsonNode apps = response.path("apps");

            int batchCount = 0;
            if (apps.isArray()) {
                for (JsonNode app : apps) {
                    long appId = app.path("appid").asLong();
                    String name = app.path("name").asText("");
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
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(properties.getApiUrl())
                .queryParam("key", properties.getApiKey())
                .queryParam("max_results", 50000)
                .queryParam("include_games", properties.isIncludeGames());

        if (lastAppId > 0) {
            b.queryParam("last_appid", lastAppId);
        }

        return b.build(true).toUriString();
    }
}


