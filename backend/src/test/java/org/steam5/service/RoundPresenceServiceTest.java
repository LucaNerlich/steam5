package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.steam5.config.PresenceProperties;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoundPresenceServiceTest {

    private RoundPresenceService service;
    private PresenceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PresenceProperties();
        properties.setMaxGlobalConnections(100);
        properties.setMaxScopeConnections(50);
        properties.setMaxIpConnections(5);
        properties.setIdleTimeoutSeconds(90);

        @SuppressWarnings("unchecked")
        final ObjectProvider<PresenceMetrics> metricsProvider = mock(ObjectProvider.class);
        when(metricsProvider.getObject()).thenReturn(mock(PresenceMetrics.class));

        service = new RoundPresenceService(new ObjectMapper(), properties, metricsProvider);
    }

    @Test
    void registerAddsSessionToItsScope() {
        final WebSocketSession session = sessionFor("2026-07-12", "127.0.0.1", null, null, null);

        assertEquals(RoundPresenceService.RegisterResult.OK, service.register(session));

        assertEquals(1, service.scopeSize("2026-07-12"));
        assertEquals(0, service.scopeSize("2026-07-13"));
    }

    @Test
    void registerIsolatesScopesFromEachOther() {
        final WebSocketSession scopeA1 = sessionFor("scopeA", "1.1.1.1", null, null, null);
        final WebSocketSession scopeA2 = sessionFor("scopeA", "1.1.1.2", null, null, null);
        final WebSocketSession scopeB1 = sessionFor("scopeB", "1.1.1.3", null, null, null);

        service.register(scopeA1);
        service.register(scopeA2);
        service.register(scopeB1);

        assertEquals(2, service.scopeSize("scopeA"));
        assertEquals(1, service.scopeSize("scopeB"));
    }

    @Test
    void unregisterRemovesSessionFromScope() {
        final WebSocketSession session = sessionFor("scopeA", "1.1.1.1", null, null, null);
        service.register(session);
        assertEquals(1, service.scopeSize("scopeA"));

        service.unregister(session);

        assertEquals(0, service.scopeSize("scopeA"));
    }

    @Test
    void registerRejectsWhenIpLimitExceeded() {
        for (int i = 0; i < 5; i++) {
            assertEquals(RoundPresenceService.RegisterResult.OK,
                    service.register(sessionFor("scopeA", "10.0.0.1", null, null, null)));
        }

        final WebSocketSession overflow = sessionFor("scopeA", "10.0.0.1", null, null, null);
        assertEquals(RoundPresenceService.RegisterResult.IP_LIMIT, service.register(overflow));
    }

    @Test
    void snapshotDeduplicatesAuthenticatedPlayersBySteamId() {
        final WebSocketSession tab1 = sessionFor("scopeA", "1.1.1.1", "76561", "Alice", "http://avatar/alice");
        final WebSocketSession tab2 = sessionFor("scopeA", "1.1.1.2", "76561", "Alice", "http://avatar/alice");
        final WebSocketSession tab3 = sessionFor("scopeA", "1.1.1.3", "99999", "Bob", "http://avatar/bob");
        final WebSocketSession anon = sessionFor("scopeA", "1.1.1.4", null, null, null);

        final RoundPresenceService.Snapshot snapshot =
                service.computeSnapshot(List.of(tab1, tab2, tab3, anon));

        assertEquals(4, snapshot.totalCount());
        assertEquals(1, snapshot.anonymousCount());
        assertEquals(3, snapshot.uniquePlayerCount());
        assertEquals(2, snapshot.players().size());
        assertEquals(List.of("76561", "99999"),
                snapshot.players().stream().map(RoundPresenceService.PlayerInfo::steamId).toList());
    }

    @Test
    void snapshotGroupsAnonymousSessionsByIpForUniqueCount() {
        final WebSocketSession anonTab1 = sessionFor("scopeA", "9.9.9.9", null, null, null);
        final WebSocketSession anonTab2 = sessionFor("scopeA", "9.9.9.9", null, null, null);

        final RoundPresenceService.Snapshot snapshot =
                service.computeSnapshot(List.of(anonTab1, anonTab2));

        assertEquals(2, snapshot.totalCount());
        assertEquals(2, snapshot.anonymousCount());
        assertEquals(1, snapshot.uniquePlayerCount());
        assertTrue(snapshot.players().isEmpty());
    }

    @Test
    void snapshotSkipsClosedSessions() {
        final WebSocketSession open = sessionFor("scopeA", "1.1.1.1", "1", "Alice", "http://avatar/a");
        final WebSocketSession closed = sessionFor("scopeA", "1.1.1.2", "2", "Bob", "http://avatar/b");
        when(closed.isOpen()).thenReturn(false);

        final RoundPresenceService.Snapshot snapshot =
                service.computeSnapshot(List.of(open, closed));

        assertEquals(1, snapshot.totalCount());
        assertEquals(1, snapshot.uniquePlayerCount());
        assertEquals(1, snapshot.players().size());
        assertEquals("1", snapshot.players().get(0).steamId());
    }

    @Test
    void broadcastPrunesClosedSessions() {
        final WebSocketSession open = sessionFor("scopeA", "1.1.1.1", "1", "Alice", "http://avatar/a");
        final WebSocketSession closed = sessionFor("scopeA", "1.1.1.2", "2", "Bob", "http://avatar/b");
        when(closed.isOpen()).thenReturn(false);

        service.register(open);
        service.register(closed);
        assertEquals(2, service.scopeSize("scopeA"));

        service.broadcastSnapshot("scopeA");

        assertEquals(1, service.scopeSize("scopeA"));
        assertTrue(service.sessionsFor("scopeA").contains(open));
    }

    @Test
    void broadcastPrunesSessionsThatFailToSend() throws IOException {
        final WebSocketSession healthy = sessionFor("scopeA", "1.1.1.1", "1", "Alice", "http://avatar/a");
        final WebSocketSession faulty = sessionFor("scopeA", "1.1.1.2", "2", "Bob", "http://avatar/b");
        doThrow(new IOException("boom")).when(faulty).sendMessage(any(TextMessage.class));

        service.register(healthy);
        service.register(faulty);

        service.broadcastSnapshot("scopeA");

        assertEquals(1, service.scopeSize("scopeA"));
        assertFalse(service.sessionsFor("scopeA").contains(faulty));
        verify(healthy, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastIsSafeWhenScopeHasNoSessions() {
        assertDoesNotThrow(() -> service.broadcastSnapshot("unknownScope"));
    }

    @Test
    void broadcastSendsFullSnapshotToAnonymousSessionsToo() throws IOException {
        final WebSocketSession authed = sessionFor("scopeA", "1.1.1.1", "1", "Alice", "http://avatar/a");
        final WebSocketSession anon = sessionFor("scopeA", "1.1.1.2", null, null, null);

        service.register(authed);
        service.register(anon);
        service.broadcastSnapshot("scopeA");

        final var authedCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(authed).sendMessage(authedCaptor.capture());
        final var authedSnapshot = new ObjectMapper().readValue(
                authedCaptor.getValue().getPayload(), RoundPresenceService.Snapshot.class);
        assertEquals(2, authedSnapshot.totalCount());
        assertEquals(2, authedSnapshot.uniquePlayerCount());
        assertEquals(1, authedSnapshot.players().size());

        final var anonCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(anon).sendMessage(anonCaptor.capture());
        final var anonSnapshot = new ObjectMapper().readValue(
                anonCaptor.getValue().getPayload(), RoundPresenceService.Snapshot.class);
        assertEquals(2, anonSnapshot.totalCount());
        assertEquals(1, anonSnapshot.anonymousCount());
        assertEquals(2, anonSnapshot.uniquePlayerCount());
        assertEquals(1, anonSnapshot.players().size());
    }

    @Test
    void sweepClosesIdleSessions() throws IOException {
        properties.setIdleTimeoutSeconds(1);
        final WebSocketSession idle = sessionFor("scopeA", "1.1.1.1", null, null, null);
        service.register(idle);
        idle.getAttributes().put(RoundPresenceService.ATTR_LAST_ACTIVITY, System.currentTimeMillis() - 5_000L);

        service.sweepIdleAndClosed();

        verify(idle).close(CloseStatus.GOING_AWAY);
        assertEquals(0, service.scopeSize("scopeA"));
    }

    @Test
    void handleClientMessageUpdatesActivityOnPing() {
        final WebSocketSession session = sessionFor("scopeA", "1.1.1.1", null, null, null);
        session.getAttributes().put(RoundPresenceService.ATTR_LAST_ACTIVITY, 1L);

        service.handleClientMessage(session, "{\"type\":\"ping\"}");

        final Long updated = (Long) session.getAttributes().get(RoundPresenceService.ATTR_LAST_ACTIVITY);
        assertNotNull(updated);
        assertTrue(updated > 1L);
    }

    private static final AtomicInteger SESSION_ID = new AtomicInteger();

    private static WebSocketSession sessionFor(final String scopeKey,
                                               final String clientIp,
                                               final String steamId,
                                               final String personaName,
                                               final String avatar) {
        final Map<String, Object> attrs = new HashMap<>();
        attrs.put(RoundPresenceService.ATTR_SCOPE_KEY, scopeKey);
        if (clientIp != null) attrs.put(RoundPresenceService.ATTR_CLIENT_IP, clientIp);
        if (steamId != null) attrs.put(RoundPresenceService.ATTR_STEAM_ID, steamId);
        if (personaName != null) attrs.put(RoundPresenceService.ATTR_PERSONA_NAME, personaName);
        if (avatar != null) attrs.put(RoundPresenceService.ATTR_AVATAR, avatar);

        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-" + SESSION_ID.incrementAndGet());
        return session;
    }
}
