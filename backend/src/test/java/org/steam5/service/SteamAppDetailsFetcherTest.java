package org.steam5.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.steam5.config.JobsConfig;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.SteamAppIndex;
import org.steam5.domain.details.SteamAppDetailService;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bulk details ingest must use the batch mode (only per-app eviction) and clear the
 * aggregate review-game cache once, including when the loop aborts mid-run.
 */
class SteamAppDetailsFetcherTest {

    private final SteamAppsConfig properties = mock(SteamAppsConfig.class);
    private final JsonHttpClient jsonHttpClient = mock(JsonHttpClient.class);
    private final IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    private final SteamAppIndexRepository appIndexRepository = mock(SteamAppIndexRepository.class);
    private final SteamAppDetailService service = mock(SteamAppDetailService.class);
    private final DomainCacheEvictor cacheEvictor = mock(DomainCacheEvictor.class);
    private final JobsConfig jobsConfig = mock(JobsConfig.class);
    private final SteamAppDetailsFetcher fetcher = new SteamAppDetailsFetcher(
            properties, jsonHttpClient, ingestStateRepository, appIndexRepository, service, cacheEvictor, jobsConfig);

    @Test
    void bulkIngestUsesTheBatchFlagAndClearsTheAggregateOnce() throws Exception {
        when(properties.getApiKey()).thenReturn("key");
        final JobsConfig.SteamAppDetails details = mock(JobsConfig.SteamAppDetails.class);
        when(jobsConfig.getSteamAppDetails()).thenReturn(details);
        when(details.getBatchLimit()).thenReturn(10);
        when(ingestStateRepository.findById("steam_app_details")).thenReturn(Optional.empty());

        final Page<SteamAppIndex> firstPage = pageOf(1L, 2L, 3L);
        when(appIndexRepository.findByAppIdGreaterThan(any(), any(PageRequest.class)))
                .thenReturn(firstPage, Page.empty());

        final JsonNode root = new ObjectMapper().readTree("""
                {"1":{"success":true,"data":{"name":"A"}},
                 "2":{"success":true,"data":{"name":"B"}},
                 "3":{"success":true,"data":{"name":"C"}}}
                """);
        when(jsonHttpClient.getJson(any())).thenReturn(root);

        fetcher.ingest();

        verify(service, times(3)).upsertFromJson(anyLong(), any(), eq(false));
        verify(cacheEvictor, times(1)).evictReviewGameState();
    }

    private static Page<SteamAppIndex> pageOf(Long... ids) {
        final List<SteamAppIndex> rows = java.util.Arrays.stream(ids).map(id -> {
            final SteamAppIndex row = mock(SteamAppIndex.class);
            when(row.getAppId()).thenReturn(id);
            return row;
        }).toList();
        return new PageImpl<>(rows);
    }
}
