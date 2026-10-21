package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.ArrayList;
import java.util.List;

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
    private final TransactionTemplate transactionTemplate;

    public SteamAppListFetcher(SteamAppsConfig properties,
                               SteamAppIndexRepository appIndexRepository,
                               IngestStateRepository ingestStateRepository,
                               JsonHttpClient jsonHttpClient,
                               JobsConfig jobsConfig,
                               PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.appIndexRepository = appIndexRepository;
        this.ingestStateRepository = ingestStateRepository;
        this.jsonHttpClient = jsonHttpClient;
        this.jobsConfig = jobsConfig;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
                final List<SteamAppIndex> pageRows = new ArrayList<>();
                for (JsonNode app : apps) {
                    final long appId = app.path("appid").asLong();
                    final String name = app.path("name").asText("");
                    if (appId <= 0 || name.isEmpty()) {
                        continue;
                    }
                    pageRows.add(new SteamAppIndex(appId, name.length() > MAX_NAME_LENGTH
                            ? name.substring(0, MAX_NAME_LENGTH)
                            : name));
                    batchCount++;
                }
                totalApps += apps.size();
                // One transaction per page instead of one per app: without it, save() commits
                // each row on its own (and, for an assigned id, merges with a SELECT first),
                // so a 50k-app page is 50k commits.
                try {
                    transactionTemplate.executeWithoutResult(status -> appIndexRepository.saveAll(pageRows));
                } catch (DataIntegrityViolationException batchFailure) {
                    // One rejected row must not wedge the page: retry row by row so the cursor
                    // still advances. Logged because a batch failure that does not reproduce per
                    // row silently degrades every page to single-row transactions.
                    log.warn("Page batch of {} rows failed ({}); retrying row by row",
                            pageRows.size(), batchFailure.getMessage());
                    persistRowByRow(pageRows);
                }
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

    private void persistRowByRow(List<SteamAppIndex> rows) {
        for (SteamAppIndex row : rows) {
            try {
                appIndexRepository.save(row);
            } catch (DataIntegrityViolationException e) {
                log.warn("Skipping appId {} rejected by steam_app_index: {}", row.getAppId(), e.getMessage());
            }
        }
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


