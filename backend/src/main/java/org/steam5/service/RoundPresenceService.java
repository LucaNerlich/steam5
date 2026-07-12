package org.steam5.service;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of active WebSocket sessions per round scope. Broadcasts
 * per-scope presence snapshots when membership changes.
 *
 * <p>Session attribute keys (populated by the handshake interceptor and the
 * WebSocket handler at connection time):</p>
 * <ul>
 *   <li>{@link #ATTR_SCOPE_KEY} — round scope in the form {@code gameDate:roundIndex:appId}</li>
 *   <li>{@link #ATTR_STEAM_ID} — verified steamId, or {@code null} for anonymous</li>
 *   <li>{@link #ATTR_PERSONA_NAME} — display name (authenticated only)</li>
 *   <li>{@link #ATTR_AVATAR} — avatar URL (authenticated only)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundPresenceService {

    public static final String ATTR_SCOPE_KEY = "scopeKey";
    public static final String ATTR_STEAM_ID = "steamId";
    public static final String ATTR_PERSONA_NAME = "personaName";
    public static final String ATTR_AVATAR = "avatar";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByScope =
            new ConcurrentHashMap<>();

    public void register(final WebSocketSession session) {
        final String scopeKey = scopeKeyOf(session);
        if (scopeKey == null) return;
        sessionsByScope
                .computeIfAbsent(scopeKey, k -> new CopyOnWriteArrayList<>())
                .add(session);
    }

    public void unregister(final WebSocketSession session) {
        final String scopeKey = scopeKeyOf(session);
        if (scopeKey == null) return;
        sessionsByScope.compute(scopeKey, (k, list) -> {
            list.remove(session);
            return list.isEmpty() ? null : list;
        });
    }

    public void broadcastSnapshot(final String scopeKey) {
        if (scopeKey == null) return;
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        if (list == null || list.isEmpty()) return;

        final Snapshot fullSnapshot = computeSnapshot(list);
        final Snapshot countsOnly = new Snapshot(fullSnapshot.totalCount(), fullSnapshot.anonymousCount(), List.of());
        final String fullPayload;
        final String countsPayload;
        try {
            fullPayload = objectMapper.writeValueAsString(fullSnapshot);
            countsPayload = objectMapper.writeValueAsString(countsOnly);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize presence snapshot for scope {}", scopeKey, e);
            return;
        }

        final TextMessage fullMessage = new TextMessage(fullPayload);
        final TextMessage countsMessage = new TextMessage(countsPayload);
        for (final WebSocketSession session : list) {
            if (!session.isOpen()) {
                list.remove(session);
                continue;
            }
            try {
                final boolean anonymous = session.getAttributes().get(ATTR_STEAM_ID) == null;
                session.sendMessage(anonymous ? countsMessage : fullMessage);
            } catch (IOException | IllegalStateException e) {
                log.debug("Pruning failed presence session {} for scope {}: {}",
                        session.getId(), scopeKey, e.toString());
                list.remove(session);
            }
        }
    }

    Snapshot computeSnapshot(final List<WebSocketSession> sessions) {
        int totalCount = 0;
        int anonymousCount = 0;
        // LinkedHashMap keeps insertion order — stable output for tests and clients.
        final Map<String, PlayerInfo> playersById = new LinkedHashMap<>();

        for (final WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            totalCount++;
            final String steamId = (String) session.getAttributes().get(ATTR_STEAM_ID);
            if (steamId == null || steamId.isBlank()) {
                anonymousCount++;
                continue;
            }
            playersById.computeIfAbsent(steamId, id -> new PlayerInfo(
                    id,
                    (String) session.getAttributes().get(ATTR_PERSONA_NAME),
                    (String) session.getAttributes().get(ATTR_AVATAR)
            ));
        }

        return new Snapshot(totalCount, anonymousCount, new ArrayList<>(playersById.values()));
    }

    int scopeSize(final String scopeKey) {
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        return list == null ? 0 : list.size();
    }

    List<WebSocketSession> sessionsFor(final String scopeKey) {
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    private static String scopeKeyOf(final WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_SCOPE_KEY);
    }

    public record PlayerInfo(String steamId, String personaName, String avatar) {
    }

    public record Snapshot(int totalCount, int anonymousCount, List<PlayerInfo> players) {
    }
}
