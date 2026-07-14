package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.config.PresenceProperties;

import static org.junit.jupiter.api.Assertions.*;

class PresenceRateLimiterTest {

    private PresenceRateLimiter limiter;
    private PresenceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PresenceProperties();
        properties.setTicketRateLimitPerMinute(2);
        properties.setTicketRateLimitPerUserPerMinute(2);
        properties.setHandshakeRateLimitPerMinute(2);
        limiter = new PresenceRateLimiter(properties);
    }

    @Test
    void ticketRateLimitIsPerIp() {
        assertTrue(limiter.tryAcquireTicket("1.2.3.4"));
        assertTrue(limiter.tryAcquireTicket("1.2.3.4"));
        assertFalse(limiter.tryAcquireTicket("1.2.3.4"));
        assertTrue(limiter.tryAcquireTicket("5.6.7.8"));
    }

    @Test
    void handshakeRateLimitIsPerIp() {
        assertTrue(limiter.tryAcquireHandshake("9.9.9.9"));
        assertTrue(limiter.tryAcquireHandshake("9.9.9.9"));
        assertFalse(limiter.tryAcquireHandshake("9.9.9.9"));
    }

    @Test
    void ticketUserRateLimitIsPerSteamId() {
        assertTrue(limiter.tryAcquireTicketForUser("76561"));
        assertTrue(limiter.tryAcquireTicketForUser("76561"));
        assertFalse(limiter.tryAcquireTicketForUser("76561"));
        assertTrue(limiter.tryAcquireTicketForUser("99999"));
    }
}
