package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentRateLimiterTest {

    private CommentRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new CommentRateLimiter();
    }

    @Test
    void commentRateLimitIsPerSteamId() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquireComment("76561"));
        }
        assertFalse(limiter.tryAcquireComment("76561"));
        assertTrue(limiter.tryAcquireComment("99999"));
    }

    @Test
    void reactionRateLimitIsPerSteamId() {
        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.tryAcquireReaction("76561"));
        }
        assertFalse(limiter.tryAcquireReaction("76561"));
        assertTrue(limiter.tryAcquireReaction("99999"));
    }

    @Test
    void blankSteamIdIsAllowedWithoutCounting() {
        assertTrue(limiter.tryAcquireComment(null));
        assertTrue(limiter.tryAcquireComment(""));
        assertTrue(limiter.tryAcquireComment("   "));
        assertTrue(limiter.tryAcquireReaction(null));
        assertTrue(limiter.tryAcquireReaction(""));
    }
}
