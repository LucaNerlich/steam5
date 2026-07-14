package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsTicketServiceTest {

    private WsTicketService service;

    @BeforeEach
    void setUp() {
        service = new WsTicketService();
    }

    @Test
    void issueAndValidateConsumesTicketForMatchingScope() {
        final String ticket = service.issueTicket("76561", "2026-07-14");

        assertEquals("76561", service.validateTicket(ticket, "2026-07-14"));
        assertNull(service.validateTicket(ticket, "2026-07-14"));
    }

    @Test
    void validateRejectsMismatchedScope() {
        final String ticket = service.issueTicket("76561", "2026-07-14");

        assertNull(service.validateTicket(ticket, "2026-07-15"));
        assertNull(service.validateTicket(ticket, "2026-07-14"));
    }

    @Test
    void validateRejectsUnknownTicket() {
        assertNull(service.validateTicket("missing", "2026-07-14"));
    }
}
