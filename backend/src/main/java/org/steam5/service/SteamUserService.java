package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.steam5.config.SteamAppsConfig;
import org.steam5.http.SteamHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Service
@RequiredArgsConstructor
@Slf4j
public class SteamUserService {

    private final SteamUserProfileWriter profileWriter;
    private final SteamHttpClient steamHttpClient;
    private final SteamAppsConfig steamAppsConfig;
    // Fix #10: inject the Spring-managed ObjectMapper so it inherits all registered
    // modules (Java Time, etc.) rather than using a bare new ObjectMapper() instance.
    private final ObjectMapper objectMapper;

    // Fix #11: run the Steam API call asynchronously so profile enrichment no longer
    // blocks the login response.
    //
    // No @Transactional here on purpose: the HTTP client retries with backoff at a 120 s
    // per-attempt timeout, so a login burst used to hold up to 8 of the 20 pooled Hikari
    // connections for minutes and stall unrelated endpoints. The fetch runs outside any
    // transaction and the write happens in SteamUserProfileWriter's own short transaction.
    @Async
    public void updateUserProfile(String steamId) {
        try {
            final String apiKey = steamAppsConfig.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Steam API key not configured; skipping persona fetch");
                return;
            }
            final String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key="
                    + apiKey + "&steamids=" + steamId;
            final String body = steamHttpClient.get(url);
            if (body == null || body.isBlank()) return;
            final JsonNode root = objectMapper.readTree(body);
            final JsonNode players = root.path("response").path("players");
            if (players.isArray() && !players.isEmpty()) {
                profileWriter.apply(steamId, players.get(0));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch persona name for {}", steamId, e);
        }
    }

}
