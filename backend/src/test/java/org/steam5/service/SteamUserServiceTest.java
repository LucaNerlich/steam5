package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.steam5.config.SteamAppsConfig;
import org.steam5.http.SteamApiException;
import org.steam5.http.SteamHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Steam fetch must happen entirely outside the write transaction: a connection held
 * across the client's retries (3 × 120 s) starves the pool during a login burst.
 */
class SteamUserServiceTest {

    private static final String STEAM_ID = "76561198000000001";

    private final SteamUserProfileWriter profileWriter = mock(SteamUserProfileWriter.class);
    private final SteamHttpClient httpClient = mock(SteamHttpClient.class);
    private final SteamAppsConfig config = new SteamAppsConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SteamUserService service;

    @BeforeEach
    void setUp() throws SteamApiException {
        ReflectionTestUtils.setField(config, "apiKey", "test-key");
        service = new SteamUserService(profileWriter, httpClient, config, objectMapper);
    }

    @Test
    void fetchesTheProfileBeforeWritingAnything() throws SteamApiException {
        when(httpClient.get(any(String.class))).thenReturn("""
                {"response": {"players": [{"personaname": "Someone", "avatar": "https://cdn/a.jpg"}]}}
                """);

        service.updateUserProfile(STEAM_ID);

        InOrder order = inOrder(httpClient, profileWriter);
        order.verify(httpClient).get(any(String.class));
        order.verify(profileWriter).apply(eq(STEAM_ID), any(JsonNode.class));
    }

    @Test
    void passesThePlayerNodeToTheWriter() throws SteamApiException {
        when(httpClient.get(any(String.class))).thenReturn("""
                {"response": {"players": [{"personaname": "Someone"}]}}
                """);

        service.updateUserProfile(STEAM_ID);

        final var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(profileWriter).apply(eq(STEAM_ID), captor.capture());
        assertEquals("Someone", captor.getValue().path("personaname").asText());
    }

    @Test
    void skipsEverythingWithoutAnApiKey() throws SteamApiException {
        ReflectionTestUtils.setField(config, "apiKey", "  ");

        service.updateUserProfile(STEAM_ID);

        verifyNoInteractions(httpClient, profileWriter);
    }

    @Test
    void skipsTheWriteWhenTheUpstreamBodyIsEmpty() throws SteamApiException {
        when(httpClient.get(any(String.class))).thenReturn(" ");

        service.updateUserProfile(STEAM_ID);

        verify(profileWriter, never()).apply(any(String.class), any(JsonNode.class));
    }

    @Test
    void skipsTheWriteWhenTheResponseCarriesNoPlayer() throws SteamApiException {
        when(httpClient.get(any(String.class))).thenReturn("""
                {"response": {"players": []}}
                """);

        service.updateUserProfile(STEAM_ID);

        verify(profileWriter, never()).apply(any(String.class), any(JsonNode.class));
    }
}
