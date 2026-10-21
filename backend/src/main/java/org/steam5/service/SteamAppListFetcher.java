package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.JobsConfig;
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

    /** steam_app_index.name is varchar(255); Steam's store names are not length-bounded. */
    private static final int MAX_NAME_LENGTH = 255;

    private final SteamAppsConfig properties;
    private final JsonHttpClient jsonHttpClient;
    private final SteamAppIndexRepository appIndexRepository;
    private final IngestStateRepository ingestStateRepository;
    private final JobsConfig jobsConfig;

    public SteamAppListFetcher(SteamAppsConfig properties,
                               SteamAppIndexRepository appIndexRepository,
                               IngestStateRepository ingestStateRepository,
                               JsonHttpClient jsonHttpClient,
                               JobsConfig jobsConfig) {
        this.properties = properties;
        this.appIndexRepository = appIndexRepository;
        this.ingestStateRepository = ingestStateRepository;
        this.jsonHttpClient = jsonHttpClient;
        this.jobsConfig = jobsConfig;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        boolean haveMore = true;
        long lastAppId = ingestStateRepository.findById("steam_app_list").map(IngestState::getLastAppId).orElse(0L);
        final int pageLimit = Math.max(1, jobsConfig.getSteamAppList().getBatchLimit());
        int page = 0;
        long totalApps = 0;

        while (haveMore && page < pageLimit) {
            page++;
            final String url = buildUrl(lastAppId);
            log.info("Fetching Steam app list page {} of {} with last_appid={} ...", page, pageLimit, lastAppId);

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
                    final String safeName = name.length() > MAX_NAME_LENGTH
                            ? name.substring(0, MAX_NAME_LENGTH)
                            : name;
                    try {
                        appIndexRepository.save(new SteamAppIndex(appId, safeName));
                    } catch (DataIntegrityViolationException e) {
                        // A single row Postgres rejects must not abort the page: the cursor is
                        // persisted after the loop, so the same row would fail every retry and
                        // wedge all later pages forever.
                        log.warn("Skipping appId {} rejected by steam_app_index: {}", appId, e.getMessage());
                    }
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

            if (page >= pageLimit && haveMore) {
                log.info("Steam app list page limit reached ({}); cursor saved for next run", pageLimit);
                break;
            }
        }

        log.info("Steam app list ingestion finished. pages={} pageLimit={} total_apps_seen={}", page, pageLimit, totalApps);
    }

    private String buildUrl(long lastAppId) {
        final UriComponentsBuilder b = UriComponentsBuilder.fromUriString(properties.getApiUrl())
                .queryParam("key", properties.getApiKey())
                .queryParam("max_results", 50000)
                .queryParam("include_games", true);

        if (lastAppId > 0) {
            b.queryParam("last_appid", lastAppId);
        }

        return b.build(true).toUriString();
    }
}


