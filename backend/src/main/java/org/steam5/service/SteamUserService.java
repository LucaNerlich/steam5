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
            final JsonNode root = objectMapper.readTree(body);
            final JsonNode players = root.path("response").path("players");
            if (players.isArray() && !players.isEmpty()) {
                final JsonNode player = players.get(0);
                final User user = existing != null ? existing : new User();
                user.setSteamId(steamId);
                user.setPersonaName(player.path("personaname").asText(null));
                user.setProfileUrl(player.path("profileurl").asText(null));
                user.setAvatar(player.path("avatar").asText(null));
                user.setAvatarMedium(player.path("avatarmedium").asText(null));
                user.setAvatarFull(player.path("avatarfull").asText(null));
                user.setAvatarHash(player.path("avatarhash").asText(null));
                user.setLastLogoffSec(player.path("lastlogoff").isNumber() ? player.path("lastlogoff").asLong() : null);
                user.setPersonaState(player.path("personastate").isNumber() ? player.path("personastate").asInt() : null);
                user.setPrimaryClanId(player.path("primaryclanid").asText(null));
                user.setTimeCreatedSec(player.path("timecreated").isNumber() ? player.path("timecreated").asLong() : null);
                user.setCountryCode(player.path("loccountrycode").asText(null));
                if (user.getCreatedAt() == null) user.setCreatedAt(java.time.OffsetDateTime.now());
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch persona name for {}: {}", steamId, e.toString());
        }
    }

    private String getApiKeySafe() {
        return steamAppsConfig.getApiKey();
    }
}


