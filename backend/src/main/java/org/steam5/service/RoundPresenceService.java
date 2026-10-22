package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory registry of active WebSocket sessions per game-day scope. Broadcasts
 * per-scope presence snapshots when membership changes.
 *
 * <p>Presence is scoped to a game day ({@code YYYY-MM-DD}). All live round pages
 * for that day share one pool so counts reflect everyone playing today's game.</p>
 *
 * <p>Session attribute keys:</p>
 * <ul>
 *   <li>{@link #ATTR_SCOPE_KEY} — game day in the form {@code YYYY-MM-DD}</li>
 *   <li>{@link #ATTR_CLIENT_IP} — client IP captured at handshake</li>
 *   <li>{@link #ATTR_STEAM_ID} — verified steamId, or {@code null} for anonymous</li>
 *   <li>{@link #ATTR_PERSONA_NAME} — display name (authenticated only)</li>
 *   <li>{@link #ATTR_AVATAR} — avatar URL (authenticated only)</li>
 *   <li>{@link #ATTR_LAST_ACTIVITY} — epoch millis of last ping/pong/connect</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundPresenceService {

    public static final String ATTR_SCOPE_KEY = "scopeKey";
    public static final String ATTR_CLIENT_IP = "clientIp";
    public static final String ATTR_STEAM_ID = "steamId";
    public static final String ATTR_PERSONA_NAME = "personaName";
    public static final String ATTR_AVATAR = "avatar";
    public static final String ATTR_LAST_ACTIVITY = "lastActivityAt";

    /** Bounded send per session: a hopelessly slow client is closed instead of buffered without limit. */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    public enum RegisterResult {
        OK,
        GLOBAL_LIMIT,
        SCOPE_LIMIT,
        IP_LIMIT
    }

    private final ObjectMapper objectMapper;
    private final org.steam5.config.PresenceProperties properties;
    private final ObjectProvider<PresenceMetrics> presenceMetrics;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByScope =
            new ConcurrentHashMap<>();
    /** Send-serializing wrapper per session id; the registry itself holds raw sessions. */
    private final ConcurrentHashMap<String, WebSocketSession> sendSessions = new ConcurrentHashMap<>();
    private final AtomicInteger globalConnections = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> connectionsByIp = new ConcurrentHashMap<>();
    private final Object registerLock = new Object();

    /**
     * Registers a WebSocket session if the configured connection limits allow it.
     *
     * @param session the WebSocket session to register
     * @return {@code OK} when registered, or the applicable limit result when registration is rejected
     */
    public RegisterResult register(final WebSocketSession session) {
        final String scopeKey = scopeKeyOf(session);
        if (scopeKey == null) return RegisterResult.SCOPE_LIMIT;

        synchronized (registerLock) {
            if (globalConnections.get() >= properties.getMaxGlobalConnections()) {
                return RegisterResult.GLOBAL_LIMIT;
            }
            if (scopeSize(scopeKey) >= properties.getMaxScopeConnections()) {
                return RegisterResult.SCOPE_LIMIT;
            }

            final String clientIp = clientIpOf(session);
            if (clientIp != null) {
                final AtomicInteger ipCount = connectionsByIp.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
                if (ipCount.get() >= properties.getMaxIpConnections()) {
                    return RegisterResult.IP_LIMIT;
                }
            }

            sessionsByScope
                    .computeIfAbsent(scopeKey, k -> new CopyOnWriteArrayList<>())
                    .add(session);
            globalConnections.incrementAndGet();
            if (clientIp != null) {
                connectionsByIp.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
            }
            touchActivity(session);
            return RegisterResult.OK;
        }
    }

    /**
     * Removes a session from its scope and updates the active connection counters.
     *
     * @param session the WebSocket session to remove
     */
    public void unregister(final WebSocketSession session) {
        final String scopeKey = scopeKeyOf(session);
        if (scopeKey == null) return;
        // Take registerLock before the scope map: register acquires them in that order, and
        // ConcurrentHashMap.computeIfPresent holds the bin monitor while this lambda runs, so
        // the opposite order here deadlocks the two paths against each other.
        synchronized (registerLock) {
            sessionsByScope.computeIfPresent(scopeKey, (k, list) -> {
                if (list.remove(session)) {
                    sendSessions.remove(session.getId());
                    decrementConnectionCounters(session);
                }
                return list.isEmpty() ? null : list;
            });
        }
    }

    /**
     * Prunes a session from a scope list and releases its send wrapper.
     *
     * @param list the scope's session list
     * @param session the session to remove
     */
    private boolean pruneSession(final CopyOnWriteArrayList<WebSocketSession> list, final WebSocketSession session) {
        if (list.remove(session)) {
            sendSessions.remove(session.getId());
            decrementConnectionCounters(session);
            return true;
        }
        return false;
    }

    /**
     * Returns the send-serializing wrapper for a session, creating it on first use.
     *
     * <p>Without it, two broadcasts for one scope can write to the same session at the
     * same time — a join/leave racing the 30s sweep, for instance — and the container
     * answers that with an {@link IllegalStateException}, which the broadcast error path
     * used to mistake for a dead session. The wrapper also bounds a slow client (5s
     * send, 64KiB buffer) instead of buffering without limit. The registry keeps the raw
     * session, so session identity is unchanged.</p>
     */
    private WebSocketSession sendSessionFor(final WebSocketSession session) {
        return sendSessions.computeIfAbsent(session.getId(),
                id -> new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
    }

    /**
     * Updates the session's last-activity timestamp.
     */
    public void touchActivity(final WebSocketSession session) {
        session.getAttributes().put(ATTR_LAST_ACTIVITY, System.currentTimeMillis());
    }

    /**
     * Processes a client message and refreshes the session activity timestamp for ping or pong messages.
     *
     * @param session the WebSocket session associated with the message
     * @param payload the client message payload
     */
    public void handleClientMessage(final WebSocketSession session, final String payload) {
        try {
            final JsonNode node = objectMapper.readTree(payload);
            final String type = node.path("type").asText(null);
            if ("ping".equals(type) || "pong".equals(type)) {
                touchActivity(session);
            }
        } catch (RuntimeException e) {
            log.debug("Ignoring non-JSON presence message on session {}: {}", session.getId(), e.toString());
        }
    }

    /**
     * Prunes closed and idle sessions, then rebroadcasts snapshots for scopes that changed.
     */
    public void sweepIdleAndClosed() {
        final long idleCutoff = System.currentTimeMillis()
                - (properties.getIdleTimeoutSeconds() * 1000L);
        final Set<String> scopesToBroadcast = new HashSet<>();

        for (final Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : sessionsByScope.entrySet()) {
            final String scopeKey = entry.getKey();
            final CopyOnWriteArrayList<WebSocketSession> list = entry.getValue();
            boolean changed = false;

            for (final WebSocketSession session : List.copyOf(list)) {
                if (!session.isOpen()) {
                    if (pruneSession(list, session)) {
                        changed = true;
                    }
                    continue;
                }
                final Long lastActivity = (Long) session.getAttributes().get(ATTR_LAST_ACTIVITY);
                if (lastActivity != null && lastActivity < idleCutoff) {
                    log.debug("Closing idle presence session {} in scope {}", session.getId(), scopeKey);
                    presenceMetrics.getObject().recordIdleTimeout();
                    try {
                        session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException e) {
                        log.debug("Failed to close idle session {}: {}", session.getId(), e.toString());
                    }
                    unregister(session);
                    changed = true;
                }
            }

            if (changed) {
                scopesToBroadcast.add(scopeKey);
            }
        }

        scopesToBroadcast.forEach(this::broadcastSnapshot);
    }

    /**
     * Broadcasts the current presence snapshot to all open sessions in a scope.
     *
     * @param scopeKey the scope whose presence snapshot should be broadcast
     */
    public void broadcastSnapshot(final String scopeKey) {
        if (scopeKey == null) return;
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        if (list == null || list.isEmpty()) return;

        final Snapshot snapshot = computeSnapshot(list);
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(snapshot);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize presence snapshot for scope {}", scopeKey, e);
            return;
        }

        final TextMessage message = new TextMessage(payload);
        for (final WebSocketSession session : list) {
            if (!session.isOpen()) {
                pruneSession(list, session);
                continue;
            }
            try {
                sendSessionFor(session).sendMessage(message);
            } catch (IOException | IllegalStateException | SessionLimitExceededException e) {
                presenceMetrics.getObject().recordBroadcastFailure();
                // Only genuinely dead sessions are pruned. With sends serialized, a failed
                // write to a still-open session is transient (the next snapshot carries the
                // full state), and pruning it would silently drop a connected client.
                // SessionLimitExceededException is the decorator's terminal state (send or
                // buffer limit breached): the session is open but will never drain, and left
                // in the list it aborts every later broadcast and keeps counting as present.
                if (!session.isOpen() || e instanceof IOException || e instanceof SessionLimitExceededException) {
                    log.debug("Pruning failed presence session {} for scope {}: {}",
                            session.getId(), scopeKey, e.toString());
                    pruneSession(list, session);
                    if (e instanceof SessionLimitExceededException limitExceeded) {
                        // The decorator only flags the session; it stays open but every later send
                        // short-circuits. Close it so the client reconnects and the socket is not
                        // held outside the counters that were just released.
                        try {
                            session.close(limitExceeded.getStatus());
                        } catch (Exception closeFailure) {
                            log.debug("Failed to close limit-exceeded session {}: {}", session.getId(), closeFailure.toString());
                        }
                    }
                } else {
                    log.debug("Broadcast to session {} failed but the session is open; keeping it: {}",
                            session.getId(), e.toString());
                }
            }
        }
    }

    /**
     * Builds a presence snapshot from the currently open sessions.
     *
     * @param sessions the sessions to include in the snapshot
     * @return a snapshot containing connection totals, anonymous session counts,
     *         unique player counts, and authenticated player information
     */
    Snapshot computeSnapshot(final List<WebSocketSession> sessions) {
        int totalCount = 0;
        int anonymousCount = 0;
        final Map<String, PlayerInfo> playersById = new LinkedHashMap<>();
        final Set<String> anonymousIps = new HashSet<>();

        for (final WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            totalCount++;
            final String steamId = (String) session.getAttributes().get(ATTR_STEAM_ID);
            if (steamId == null || steamId.isBlank()) {
                anonymousCount++;
                final String ip = clientIpOf(session);
                if (ip != null && !ip.isBlank()) {
                    anonymousIps.add(ip);
                } else {
                    anonymousIps.add("anon:" + session.getId());
                }
                continue;
            }
            playersById.computeIfAbsent(steamId, id -> new PlayerInfo(
                    id,
                    (String) session.getAttributes().get(ATTR_PERSONA_NAME),
                    (String) session.getAttributes().get(ATTR_AVATAR)
            ));
        }

        final int uniquePlayerCount = playersById.size() + anonymousIps.size();
        return new Snapshot(totalCount, anonymousCount, uniquePlayerCount, new ArrayList<>(playersById.values()));
    }

    /**
     * Gets the number of currently active registered connections.
     *
     * @return the active connection count
     */
    public int activeConnectionCount() {
        return globalConnections.get();
    }

    /**
     * Gets the number of sessions registered for a scope.
     *
     * @param scopeKey the scope key to query
     * @return the number of registered sessions, or {@code 0} if the scope has no sessions
     */
    int scopeSize(final String scopeKey) {
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        return list == null ? 0 : list.size();
    }

    /**
     * Retrieves the sessions registered under a scope.
     *
     * @param scopeKey the scope whose sessions should be retrieved
     * @return an immutable snapshot of the sessions for the scope, or an empty list when no sessions are registered
     */
    List<WebSocketSession> sessionsFor(final String scopeKey) {
        final CopyOnWriteArrayList<WebSocketSession> list = sessionsByScope.get(scopeKey);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Decrements the global connection count and the session's client IP count.
     *
     * @param session the session whose connection counters are being removed
     */
    private void decrementConnectionCounters(final WebSocketSession session) {
        globalConnections.updateAndGet(current -> Math.max(0, current - 1));
        final String clientIp = clientIpOf(session);
        if (clientIp != null) {
            // Same lock as register: check/increment/decrement/remove must be one critical
            // section, or a decrement that drops the entry can race a concurrent register and
            // strand the new session's counter outside the map.
            synchronized (registerLock) {
                connectionsByIp.computeIfPresent(clientIp, (k, count) -> {
                    final int next = count.decrementAndGet();
                    return next <= 0 ? null : count;
                });
            }
        }
    }

    /**
     * Retrieves the scope key associated with a WebSocket session.
     *
     * @param session the WebSocket session whose scope key is read
     * @return the session's scope key, or {@code null} if none is associated
     */
    private static String scopeKeyOf(final WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_SCOPE_KEY);
    }

    /**
     * Retrieves the client IP address associated with a WebSocket session.
     *
     * @param session the WebSocket session
     * @return the client IP address, or {@code null} if none is associated
     */
    private static String clientIpOf(final WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_CLIENT_IP);
    }

    public record PlayerInfo(String steamId, String personaName, String avatar) {
    }

    public record Snapshot(int totalCount, int anonymousCount, int uniquePlayerCount, List<PlayerInfo> players) {
    }
}
