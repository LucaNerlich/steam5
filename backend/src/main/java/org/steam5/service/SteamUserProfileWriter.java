package org.steam5.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.steam5.domain.User;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.UserRepository;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Persists one GetPlayerSummaries snapshot in a short transaction.
 *
 * <p>Split out of {@link SteamUserService} so the Steam HTTP call can never run inside a
 * transaction: {@code SteamHttpClient} retries with backoff at a 120 s per-attempt timeout,
 * so a login burst used to pin pooled Hikari connections for minutes and stall unrelated
 * requests. Nothing here does I/O — the caller fetches first.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SteamUserProfileWriter {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Applies the player node of a GetPlayerSummaries response, creating the user row when it
     * does not exist yet and enqueueing the avatar blurhash when the avatar changed.
     *
     * @param steamId SteamID64 as it was requested
     * @param player  the first entry of {@code response.players}
     */
    @Transactional
    public void apply(String steamId, JsonNode player) {
        final User existing = userRepository.findById(steamId).orElse(null);
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
        // Fix #12 (dead code removed): createdAt is set by @PrePersist on the entity;
        // the null guard here was never reachable for new User() instances.
        userRepository.save(user);

        // Determine whether avatar values changed during this update run
        final boolean avatarChanged = !Objects.equals(previousAvatar, user.getAvatar());
        final boolean avatarFullChanged = !Objects.equals(previousAvatarFull, user.getAvatarFull());

        if (StringUtils.isBlank(user.getBlurdataAvatarFull()) || avatarChanged || avatarFullChanged) {
            log.info("User {} avatar changed, trigger blurhash generation: avatarChanged={} avatarFullChanged={}",
                    steamId, avatarChanged, avatarFullChanged);
            // Publish an event to enqueue Blurhash Avatar job only when avatar fields changed
            eventPublisher.publishEvent(new BlurhashEncodeRequested(null, steamId, BlurhashEnqueueListener.Type.AVATAR));
        }
    }
}
