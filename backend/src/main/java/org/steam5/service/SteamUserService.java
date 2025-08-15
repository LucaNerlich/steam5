package org.steam5.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.User;
import org.steam5.http.SteamHttpClient;
import org.steam5.repository.UserRepository;


@Service
@RequiredArgsConstructor
@Slf4j
public class SteamUserService {

    private final UserRepository userRepository;
    private final SteamHttpClient steamHttpClient;
    private final SteamAppsConfig steamAppsConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void ensurePersonaName(String steamId) {
        try {
            final User existing = userRepository.findById(steamId).orElse(null);
            if (existing != null && existing.getPersonaName() != null && !existing.getPersonaName().isBlank()) return;

            final String apiKey = getApiKeySafe();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Steam API key not configured; skipping persona fetch");
                return;
            }
            final String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key="
                    + apiKey + "&steamids=" + steamId;
            final String body = steamHttpClient.get(url);
            if (body == null || body.isBlank()) return;
            JsonNode root = objectMapper.readTree(body);
            JsonNode players = root.path("response").path("players");
            if (players.isArray() && !players.isEmpty()) {
                String persona = players.get(0).path("personaname").asText(null);
                if (persona != null && !persona.isBlank()) {
                    User u = existing != null ? existing : new User();
                    u.setSteamId(steamId);
                    u.setPersonaName(persona);
                    if (u.getCreatedAt() == null) u.setCreatedAt(java.time.OffsetDateTime.now());
                    userRepository.save(u);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch persona name for {}: {}", steamId, e.toString());
        }
    }

    private String getApiKeySafe() {
        return steamAppsConfig.getApiKey();
    }
}


