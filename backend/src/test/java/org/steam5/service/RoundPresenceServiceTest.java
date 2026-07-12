package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
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

    @BeforeEach
    void setUp() {
        service = new RoundPresenceService(new ObjectMapper());
    }

    @Test
    void registerAddsSessionToItsScope() {
        final WebSocketSession session = sessionFor("2026-07-12:1:730", null, null, null);

        service.register(session);

        assertEquals(1, service.scopeSize("2026-07-12:1:730"));
        assertEquals(0, service.scopeSize("2026-07-12:1:570"));
    }

    @Test
    void registerIsolatesScopesFromEachOther() {
        final WebSocketSession scopeA1 = sessionFor("scopeA", null, null, null);
        final WebSocketSession scopeA2 = sessionFor("scopeA", null, null, null);
        final WebSocketSession scopeB1 = sessionFor("scopeB", null, null, null);

        service.register(scopeA1);
        service.register(scopeA2);
        service.register(scopeB1);

        assertEquals(2, service.scopeSize("scopeA"));
        assertEquals(1, service.scopeSize("scopeB"));
    }

    @Test
    void unregisterRemovesSessionFromScope() {
        final WebSocketSession session = sessionFor("scopeA", null, null, null);
        service.register(session);
        assertEquals(1, service.scopeSize("scopeA"));

        service.unregister(session);

        assertEquals(0, service.scopeSize("scopeA"));
    }

    @Test
    void snapshotDeduplicatesAuthenticatedPlayersBySteamId() {
        final WebSocketSession tab1 = sessionFor("scopeA", "76561", "Alice", "http://avatar/alice");
        final WebSocketSession tab2 = sessionFor("scopeA", "76561", "Alice", "http://avatar/alice");
        final WebSocketSession tab3 = sessionFor("scopeA", "99999", "Bob", "http://avatar/bob");
        final WebSocketSession anon = sessionFor("scopeA", null, null, null);

        final RoundPresenceService.Snapshot snapshot =
                service.computeSnapshot(List.of(tab1, tab2, tab3, anon));

        assertEquals(4, snapshot.totalCount());
        assertEquals(1, snapshot.anonymousCount());
        assertEquals(2, snapshot.players().size());
        assertEquals(List.of("76561", "99999"),
                snapshot.players().stream().map(RoundPresenceService.PlayerInfo::steamId).toList());
    }

    @Test
    void snapshotSkipsClosedSessions() {
        final WebSocketSession open = sessionFor("scopeA", "1", "Alice", "http://avatar/a");
        final WebSocketSession closed = sessionFor("scopeA", "2", "Bob", "http://avatar/b");
        when(closed.isOpen()).thenReturn(false);

        final RoundPresenceService.Snapshot snapshot =
                service.computeSnapshot(List.of(open, closed));

        assertEquals(1, snapshot.totalCount());
        assertEquals(1, snapshot.players().size());
        assertEquals("1", snapshot.players().get(0).steamId());
    }

    @Test
    void broadcastPrunesClosedSessions() {
        final WebSocketSession open = sessionFor("scopeA", "1", "Alice", "http://avatar/a");
        final WebSocketSession closed = sessionFor("scopeA", "2", "Bob", "http://avatar/b");
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
        final WebSocketSession healthy = sessionFor("scopeA", "1", "Alice", "http://avatar/a");
        final WebSocketSession faulty = sessionFor("scopeA", "2", "Bob", "http://avatar/b");
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
    void broadcastSendsCountsOnlyToAnonymousSessions() throws IOException {
        final WebSocketSession authed = sessionFor("scopeA", "1", "Alice", "http://avatar/a");
        final WebSocketSession anon = sessionFor("scopeA", null, null, null);

        service.register(authed);
        service.register(anon);
        service.broadcastSnapshot("scopeA");

        // Authenticated session receives full snapshot
        final var authedCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(authed).sendMessage(authedCaptor.capture());
        final var authedSnapshot = new ObjectMapper().readValue(
                authedCaptor.getValue().getPayload(), RoundPresenceService.Snapshot.class);
        assertEquals(2, authedSnapshot.totalCount());
        assertEquals(1, authedSnapshot.players().size());

        // Anonymous session receives counts-only snapshot
        final var anonCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(anon).sendMessage(anonCaptor.capture());
        final var anonSnapshot = new ObjectMapper().readValue(
                anonCaptor.getValue().getPayload(), RoundPresenceService.Snapshot.class);
        assertEquals(2, anonSnapshot.totalCount());
        assertEquals(1, anonSnapshot.anonymousCount());
        assertTrue(anonSnapshot.players().isEmpty());
    }

    private static final AtomicInteger SESSION_ID = new AtomicInteger();

    private static WebSocketSession sessionFor(final String scopeKey,
                                               final String steamId,
                                               final String personaName,
                                               final String avatar) {
        final Map<String, Object> attrs = new HashMap<>();
        attrs.put(RoundPresenceService.ATTR_SCOPE_KEY, scopeKey);
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
