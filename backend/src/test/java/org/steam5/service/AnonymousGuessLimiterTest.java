package org.steam5.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousGuessLimiterTest {

    @Test
    void firstClaimForAnIpAndAppSucceeds() {
        final AnonymousGuessLimiter limiter = new AnonymousGuessLimiter();
        assertTrue(limiter.tryClaim("203.0.113.7", 42L));
    }

    @Test
    void secondClaimForTheSameIpAndAppFails() {
        final AnonymousGuessLimiter limiter = new AnonymousGuessLimiter();
        assertTrue(limiter.tryClaim("203.0.113.7", 42L));
        assertFalse(limiter.tryClaim("203.0.113.7", 42L));
    }

    @Test
    void sameIpDifferentAppIsIndependentlyClaimable() {
        final AnonymousGuessLimiter limiter = new AnonymousGuessLimiter();
        assertTrue(limiter.tryClaim("203.0.113.7", 42L));
        assertTrue(limiter.tryClaim("203.0.113.7", 99L));
    }

    @Test
    void differentIpSameAppIsIndependentlyClaimable() {
        final AnonymousGuessLimiter limiter = new AnonymousGuessLimiter();
        assertTrue(limiter.tryClaim("203.0.113.7", 42L));
        assertTrue(limiter.tryClaim("198.51.100.9", 42L));
    }
}
