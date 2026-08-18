package org.steam5.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenIdNonceStoreTest {

    private static final String BASE = "https://steam5.org/api/auth/steam/callback";

    @Test
    void issueAndConsumeReturnsBoundBase() {
        final OpenIdNonceStore store = new OpenIdNonceStore();
        final String nonce = store.issue(BASE);
        assertNotNull(nonce);
        assertEquals(BASE, store.consume(nonce));
    }

    @Test
    void nonceIsSingleUse() {
        final OpenIdNonceStore store = new OpenIdNonceStore();
        final String nonce = store.issue(BASE);
        assertEquals(BASE, store.consume(nonce));
        assertNull(store.consume(nonce));
    }

    @Test
    void unknownOrNullNonceConsumesToNull() {
        final OpenIdNonceStore store = new OpenIdNonceStore();
        assertNull(store.consume("does-not-exist"));
        assertNull(store.consume(null));
    }

    @Test
    void eachIssueProducesDistinctNonce() {
        final OpenIdNonceStore store = new OpenIdNonceStore();
        final String a = store.issue(BASE);
        final String b = store.issue(BASE);
        assertNotEquals(a, b);
    }
}
