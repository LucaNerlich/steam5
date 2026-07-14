package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.service.PresenceMetrics;
import org.steam5.service.PresenceRateLimiter;
import org.steam5.service.WsTicketService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PresenceAuthControllerTest {

    private WsTicketService wsTicketService;
    private PresenceRateLimiter presenceRateLimiter;
    private PresenceMetrics presenceMetrics;
    private PresenceAuthController controller;

    @BeforeEach
    void setUp() {
        wsTicketService = mock(WsTicketService.class);
        presenceRateLimiter = mock(PresenceRateLimiter.class);
        presenceMetrics = mock(PresenceMetrics.class);
        controller = new PresenceAuthController(wsTicketService, presenceRateLimiter, presenceMetrics);
        when(presenceRateLimiter.tryAcquireTicketForUser(any())).thenReturn(true);
    }

    @Test
    void issueTicket_returns200WithTicketWhenAuthenticated() {
        when(wsTicketService.issueTicket("76561", "2026-07-14")).thenReturn("ticket-abc");

        ResponseEntity<?> response = controller.issueTicket("76561", Map.of("scopeKey", "2026-07-14"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("ticket"));
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
        verify(presenceMetrics).recordTicketIssued();
    }

    @Test
    void issueTicket_returns401WhenUnauthenticated() {
        ResponseEntity<?> response = controller.issueTicket(null, Map.of("scopeKey", "2026-07-14"));

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertEquals("unauthenticated", ((Map<?, ?>) response.getBody()).get("error"));
        verify(wsTicketService, never()).issueTicket(any(), any());
    }

    @Test
    void issueTicket_rejectsInvalidScopeKey() {
        ResponseEntity<?> response = controller.issueTicket("76561", Map.of("scopeKey", "bad"));

        assertEquals(400, response.getStatusCode().value());
        verify(wsTicketService, never()).issueTicket(any(), any());
    }

    @Test
    void issueTicket_returns429WhenUserRateLimited() {
        when(presenceRateLimiter.tryAcquireTicketForUser("76561")).thenReturn(false);

        ResponseEntity<?> response = controller.issueTicket("76561", Map.of("scopeKey", "2026-07-14"));

        assertEquals(429, response.getStatusCode().value());
        verify(wsTicketService, never()).issueTicket(any(), any());
    }

    @Test
    void issueTicket_delegatesToWsTicketService() {
        when(wsTicketService.issueTicket("76561", "2026-07-14")).thenReturn("ticket-xyz");

        ResponseEntity<?> response = controller.issueTicket("76561", Map.of("scopeKey", "2026-07-14"));

        verify(wsTicketService, times(1)).issueTicket(eq("76561"), eq("2026-07-14"));
        assertNotNull(response.getBody());
        assertEquals("ticket-xyz", ((Map<?, ?>) response.getBody()).get("ticket"));
    }
}
