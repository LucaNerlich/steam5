package org.steam5.web.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.RoundPresenceService;
import org.steam5.service.WsTicketService;

import java.util.Optional;

/**
 * WebSocket handler for the per-round presence endpoint. Resolves the
 * connecting identity from the handshake ticket, registers the session with
 * {@link RoundPresenceService}, and rebroadcasts the snapshot on join/leave.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceWebSocketHandler extends TextWebSocketHandler {

    private final RoundPresenceService presenceService;
    private final WsTicketService wsTicketService;
    private final UserRepository userRepository;

    @Override
    public void afterConnectionEstablished(final WebSocketSession session) {
        final String scopeKey = (String) session.getAttributes().get(RoundPresenceService.ATTR_SCOPE_KEY);
        final String ticket = (String) session.getAttributes().get("ticket");

        final String steamId = ticket == null ? null : wsTicketService.validateTicket(ticket);
        if (steamId != null) {
            session.getAttributes().put(RoundPresenceService.ATTR_STEAM_ID, steamId);
            try {
                final Optional<User> user = userRepository.findById(steamId);
                user.ifPresent(u -> {
                    session.getAttributes().put(RoundPresenceService.ATTR_PERSONA_NAME, u.getPersonaName());
                    final String avatarUrl = (u.getAvatarFull() != null && !u.getAvatarFull().isBlank())
                            ? u.getAvatarFull() : u.getAvatar();
                    session.getAttributes().put(RoundPresenceService.ATTR_AVATAR, avatarUrl);
                });
            } catch (Exception e) {
                log.debug("Failed to load user {} for presence session {}: {}",
                        steamId, session.getId(), e.toString());
            }
        }

        presenceService.register(session);
        presenceService.broadcastSnapshot(scopeKey);
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) {
        final String scopeKey = (String) session.getAttributes().get(RoundPresenceService.ATTR_SCOPE_KEY);
        presenceService.unregister(session);
        presenceService.broadcastSnapshot(scopeKey);
    }

    @Override
    public void handleTransportError(final WebSocketSession session, final Throwable exception) {
        log.warn("Presence WebSocket transport error on session {}: {}", session.getId(), exception.toString());
        presenceService.unregister(session);
    }
}
