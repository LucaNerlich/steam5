package org.steam5.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

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
            throw new IOException("Empty response body for url=" + url);
        }
        return objectMapper.readTree(body);
    }
}


