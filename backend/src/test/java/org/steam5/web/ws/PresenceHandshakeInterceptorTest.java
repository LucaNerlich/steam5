package org.steam5.web.ws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresenceHandshakeInterceptorTest {

    @Test
    void scopeKeyPatternAcceptsGameDay() {
        assertTrue(PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN.matcher("2026-07-14").matches());
    }

    @Test
    void scopeKeyPatternAcceptsOptionalRoundSuffix() {
        assertTrue(PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN.matcher("2026-07-14:1:730").matches());
    }

    @Test
    void scopeKeyPatternRejectsMalformedValues() {
        assertFalse(PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN.matcher("07-14-2026").matches());
        assertFalse(PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN.matcher("2026-07-14:abc").matches());
    }

    @Test
    void extractParamDecodesScopeKey() {
        assertEquals("2026-07-14", PresenceHandshakeInterceptor.extractParam("scopeKey=2026-07-14&ticket=abc", "scopeKey"));
        assertEquals("abc", PresenceHandshakeInterceptor.extractParam("scopeKey=2026-07-14&ticket=abc", "ticket"));
        assertNull(PresenceHandshakeInterceptor.extractParam(null, "scopeKey"));
    }

    @Test
    void ticketSubprotocolExtractsPrefixedToken() {
        assertEquals("s5ticket.jwt.value",
                PresenceHandshakeInterceptor.ticketSubprotocol("s5ticket.jwt.value"));
    }

    @Test
    void ticketSubprotocolHandlesMultipleOfferedProtocols() {
        assertEquals("s5ticket.jwt.value",
                PresenceHandshakeInterceptor.ticketSubprotocol("chat, s5ticket.jwt.value"));
        assertEquals("s5ticket.jwt.value",
                PresenceHandshakeInterceptor.ticketSubprotocol("s5ticket.jwt.value, chat"));
    }

    @Test
    void ticketSubprotocolReturnsNullWhenAbsentOrEmpty() {
        assertNull(PresenceHandshakeInterceptor.ticketSubprotocol(null));
        assertNull(PresenceHandshakeInterceptor.ticketSubprotocol("chat"));
        assertNull(PresenceHandshakeInterceptor.ticketSubprotocol("s5ticket."));
        assertNull(PresenceHandshakeInterceptor.ticketSubprotocol(""));
    }

    @Test
    void loggableClientRefHashesIpWithoutEchoingRawValue() {
        final String hashed = PresenceHandshakeInterceptor.loggableClientRef("203.0.113.10");

        assertTrue(hashed.startsWith("ipHash:"));
        assertNotEquals("203.0.113.10", hashed);
        assertEquals(hashed, PresenceHandshakeInterceptor.loggableClientRef("203.0.113.10"));
    }

    @Test
    void loggableClientRefReturnsUnknownForBlankInput() {
        assertEquals("unknown", PresenceHandshakeInterceptor.loggableClientRef(null));
        assertEquals("unknown", PresenceHandshakeInterceptor.loggableClientRef("  "));
    }
}
