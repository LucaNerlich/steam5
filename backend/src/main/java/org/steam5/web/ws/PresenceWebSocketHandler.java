package org.steam5.web.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.PresenceMetrics;
import org.steam5.service.RoundPresenceService;
import org.steam5.service.WsTicketService;

import java.util.Optional;

/**
 * WebSocket handler for the per-day presence endpoint. Resolves the connecting
 * identity from the handshake ticket, registers the session with
 * {@link RoundPresenceService}, and rebroadcasts the snapshot on join/leave.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceWebSocketHandler extends TextWebSocketHandler {

    private final RoundPresenceService presenceService;
    private final WsTicketService wsTicketService;
    private final UserRepository userRepository;
    private final PresenceMetrics presenceMetrics;

    /**
     * Authenticates and registers a presence session, enriching it with identity details when available.
     *
     * <p>Sessions with invalid tickets or rejected registrations are closed with a policy-violation
     * status. A presence snapshot is broadcast after successful registration.</p>
     *
     * @param session the newly established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(final WebSocketSession session) {
        final String scopeKey = (String) session.getAttributes().get(RoundPresenceService.ATTR_SCOPE_KEY);
        final String ticket = (String) session.getAttributes().get("ticket");

        if (ticket != null && !ticket.isBlank()) {
            final String steamId = wsTicketService.validateTicket(ticket, scopeKey);
            if (steamId != null && !steamId.isBlank()) {
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
            } else if (steamId == null) {
                log.debug("Rejecting presence session {} — invalid ticket", session.getId());
                presenceMetrics.recordTicketInvalid();
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (Exception e) {
                    log.debug("Failed to close session with invalid ticket {}: {}", session.getId(), e.toString());
                }
                return;
            }
        }

        final RoundPresenceService.RegisterResult result = presenceService.register(session);
        if (result != RoundPresenceService.RegisterResult.OK) {
            log.debug("Rejecting presence session {} — {}", session.getId(), result);
            presenceMetrics.recordRegistrationRejected(result.name());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception e) {
                log.debug("Failed to close over-limit session {}: {}", session.getId(), e.toString());
            }
            return;
        }

        presenceService.broadcastSnapshot(scopeKey);
    }

    /**
     * Processes a text message received from a WebSocket client.
     *
     * @param session the WebSocket session that sent the message
     * @param message the received text message
     */
    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage message) {
        presenceService.handleClientMessage(session, message.getPayload());
    }

    /**
     * Removes the closed session from presence tracking and broadcasts the updated presence snapshot.
     *
     * @param session the closed WebSocket session
     * @param status  the reason the connection was closed
     */
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
