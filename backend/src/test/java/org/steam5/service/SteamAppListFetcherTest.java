package org.steam5.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.steam5.config.JobsConfig;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.SteamAppIndex;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.SteamAppIndexRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One row Postgres rejects must not abort the page: the cursor is persisted after the loop, so
 * the same row would fail every retry and wedge all later pages forever.
 */
class SteamAppListFetcherTest {

    private final SteamAppsConfig properties = mock(SteamAppsConfig.class);
    private final JsonHttpClient jsonHttpClient = mock(JsonHttpClient.class);
    private final IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    private final SteamAppIndexRepository appIndexRepository = mock(SteamAppIndexRepository.class);
    private final JobsConfig jobsConfig = new JobsConfig();
    private final SteamAppListFetcher fetcher = new SteamAppListFetcher(
            properties, appIndexRepository, ingestStateRepository, jsonHttpClient, jobsConfig);

    @Test
    void clampsAnOversizedNameAndAdvancesTheCursorPastARejectedRow() throws Exception {
        when(properties.getApiKey()).thenReturn("key");
        when(properties.getApiUrl()).thenReturn("https://example.test/");
        when(ingestStateRepository.findById("steam_app_list")).thenReturn(Optional.empty());
        final String longName = "x".repeat(300);
        final JsonNode page = new ObjectMapper().readTree("""
                {"response":{"apps":[{"appid":1,"name":"%s"},{"appid":2,"name":"ok"}],
                 "have_more_results":false,"last_appid":2}}
                """.formatted(longName));
        when(jsonHttpClient.getJson(any())).thenReturn(page);
        when(appIndexRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("row rejected"))
                .thenReturn(null);

        fetcher.ingest();

        final ArgumentCaptor<SteamAppIndex> saved = ArgumentCaptor.forClass(SteamAppIndex.class);
        verify(appIndexRepository, times(2)).save(saved.capture());
        assertEquals(255, saved.getAllValues().get(0).getName().length(),
                "an over-long Steam name must be clamped to the column width");
        verify(ingestStateRepository).upsert(eq("steam_app_list"), eq(2L), any());
    }
}
