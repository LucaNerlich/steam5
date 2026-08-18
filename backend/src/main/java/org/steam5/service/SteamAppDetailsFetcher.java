package org.steam5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.config.JobsConfig;
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
    private final JobsConfig jobsConfig;

    public SteamAppDetailsFetcher(final SteamAppsConfig properties, final JsonHttpClient jsonHttpClient, final IngestStateRepository ingestStateRepository, final SteamAppIndexRepository appIndexRepository, final SteamAppDetailService service, final JobsConfig jobsConfig) {
        this.properties = properties;
        this.jsonHttpClient = jsonHttpClient;
        this.ingestStateRepository = ingestStateRepository;
        this.appIndexRepository = appIndexRepository;
        this.service = service;
        this.jobsConfig = jobsConfig;
    }

    @Override
    public void ingest() throws IOException {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("STEAM_API_KEY must be configured");
        }

        final long lastAppId = ingestStateRepository.findById("steam_app_details").map(IngestState::getLastAppId).orElse(0L);
        final int batchLimit = Math.max(1, jobsConfig.getSteamAppDetails().getBatchLimit());

        // The cursor is monotonic per pass; once it points past the last app in
        // the index every run would be a permanent no-op, leaving prices,
        // screenshots, and descriptions stale forever. Reset it to 0 so the next
        // run re-walks the index as a refresh pass.
        long effectiveLastAppId = lastAppId;
        if (effectiveLastAppId > 0) {
            final Page<SteamAppIndex> ahead = appIndexRepository.findByAppIdGreaterThan(effectiveLastAppId, PageRequest.of(0, 1, Sort.by("appId").ascending()));
            if (ahead.isEmpty()) {
                log.info("Details ingest cursor {} is at/after the last indexed appId; resetting to 0 for the next refresh pass", effectiveLastAppId);
                ingestStateRepository.upsert("steam_app_details", 0L, OffsetDateTime.now());
                effectiveLastAppId = 0L;
            }
        }

        log.info("Starting details ingestion from appId > {} (batchLimit={})", effectiveLastAppId, batchLimit);

        long processed = 0L;
        Long cursor = effectiveLastAppId;
        final int pageSize = 500; // single HTTP call per app, moderate batch size
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
                try {
                    fetchForAppId(appId);
                } catch (Exception e) {
                    // bail immediately on any HTTP error such as 429. The cursor is
                    // deliberately NOT advanced: the failed app must be retried on
                    // the next run instead of being skipped forever.
                    throw e;
                }
                // advance cursor only on success so transient failures are retried
                ingestStateRepository.upsert("steam_app_details", appId, OffsetDateTime.now());
                processed++;
                cursor = appId;
            }
            more = page.hasNext() && processed < batchLimit;
        }

        log.info("Details ingestion finished. processed={} batchLimit={} starting_after={}", processed, batchLimit, effectiveLastAppId);
    }

    public boolean fetchForAppId(final Long appId) throws IOException {
        final String url = UriComponentsBuilder.fromUriString("https://store.steampowered.com/api/appdetails")
                .queryParam("appids", appId)
                .queryParam("key", properties.getApiKey())
                .build(true)
                .toUriString();

        final JsonNode root = jsonHttpClient.getJson(url);
        final JsonNode appNode = root.path(String.valueOf(appId));
        if (!"true".equals(appNode.path("success").asString())) {
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
