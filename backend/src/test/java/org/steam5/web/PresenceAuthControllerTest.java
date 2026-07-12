package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.service.WsTicketService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceAuthControllerTest {

    private WsTicketService wsTicketService;
    private PresenceAuthController controller;

    @BeforeEach
    void setUp() {
        wsTicketService = mock(WsTicketService.class);
        controller = new PresenceAuthController(wsTicketService);
    }

    @Test
    void issueTicket_returns200WithTicketWhenAuthenticated() {
        when(wsTicketService.issueTicket("76561")).thenReturn("ticket-abc");

        ResponseEntity<?> response = controller.issueTicket("76561");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("ticket"));
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    void issueTicket_returns401WhenUnauthenticated() {
        ResponseEntity<?> response = controller.issueTicket(null);

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertEquals("unauthenticated", ((Map<?, ?>) response.getBody()).get("error"));
        verify(wsTicketService, never()).issueTicket(any());
    }

    @Test
    void issueTicket_delegatesToWsTicketService() {
        when(wsTicketService.issueTicket("76561")).thenReturn("ticket-xyz");

        ResponseEntity<?> response = controller.issueTicket("76561");

        verify(wsTicketService, times(1)).issueTicket("76561");
        assertNotNull(response.getBody());
        assertEquals("ticket-xyz", ((Map<?, ?>) response.getBody()).get("ticket"));
    }
}
