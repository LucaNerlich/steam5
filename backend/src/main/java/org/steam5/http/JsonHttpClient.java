package org.steam5.http;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JsonHttpClient {

    private final SteamHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JsonHttpClient(SteamHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode getJson(String url) throws IOException {
        final String body = httpClient.get(url);
        if (body == null || body.isBlank()) {
            // Never embed the raw URL — it contains key=<apiKey> and this
            // message is logged by every caller.
            throw new IOException("Empty response body for url=" + SteamHttpClient.sanitizeUrl(url));
        }
        return objectMapper.readTree(body);
    }
}


