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

    public void updateUserProfile(String steamId) {
        try {
            final User existing = userRepository.findById(steamId).orElse(null);

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
                JsonNode p = players.get(0);
                User u = existing != null ? existing : new User();
                u.setSteamId(steamId);
                u.setPersonaName(p.path("personaname").asText(null));
                u.setProfileUrl(p.path("profileurl").asText(null));
                u.setAvatar(p.path("avatar").asText(null));
                u.setAvatarMedium(p.path("avatarmedium").asText(null));
                u.setAvatarFull(p.path("avatarfull").asText(null));
                u.setAvatarHash(p.path("avatarhash").asText(null));
                u.setLastLogoffSec(p.path("lastlogoff").isNumber() ? p.path("lastlogoff").asLong() : null);
                u.setPersonaState(p.path("personastate").isNumber() ? p.path("personastate").asInt() : null);
                u.setPrimaryClanId(p.path("primaryclanid").asText(null));
                u.setTimeCreatedSec(p.path("timecreated").isNumber() ? p.path("timecreated").asLong() : null);
                u.setCountryCode(p.path("loccountrycode").asText(null));
                if (u.getCreatedAt() == null) u.setCreatedAt(java.time.OffsetDateTime.now());
                userRepository.save(u);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch persona name for {}: {}", steamId, e.toString());
        }
    }

    private String getApiKeySafe() {
        return steamAppsConfig.getApiKey();
    }
}


