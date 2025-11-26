package org.steam5.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.steam5.config.SteamAppsConfig;
import org.steam5.domain.User;
import org.steam5.http.SteamHttpClient;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;


@Service
@RequiredArgsConstructor
@Slf4j
public class SteamUserService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final SteamHttpClient steamHttpClient;
    private final SteamAppsConfig steamAppsConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
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
                // Capture previous avatar values (if any) to detect changes in this update run
                final String previousAvatar = existing != null ? existing.getAvatar() : null;
                final String previousAvatarFull = existing != null ? existing.getAvatarFull() : null;

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

                // Determine whether avatar values changed during this update run
                final boolean avatarChanged = !Objects.equals(previousAvatar, user.getAvatar());
                final boolean avatarFullChanged = !Objects.equals(previousAvatarFull, user.getAvatarFull());

                if (StringUtils.isBlank(user.getBlurdataAvatarFull()) || avatarChanged || avatarFullChanged) {
                    log.info("User {} avatar changed, trigger blurhash generation: avatarChanged={} avatarFullChanged={}", steamId, avatarChanged, avatarFullChanged);
                    // Publish an event to enqueue Blurhash Avatar job only when avatar fields changed
                    eventPublisher.publishEvent(new BlurhashEncodeRequested(null, steamId, BlurhashEnqueueListener.Type.AVATAR));
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


