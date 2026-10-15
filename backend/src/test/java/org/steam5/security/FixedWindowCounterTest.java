package org.steam5.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one fixed-window primitive behind every request limiter (the per-IP servlet filters, the
 * presence limiters and the comment limiter). Its window length and memory bound are part of the
 * contract those callers rely on.
 */
class FixedWindowCounterTest {

    @Test
    void allowsExactlyTheLimitThenRejects() {
        final FixedWindowCounter counter = new FixedWindowCounter(3);

        assertTrue(counter.tryAcquire("1.2.3.4"), "1st");
        assertTrue(counter.tryAcquire("1.2.3.4"), "2nd");
        assertTrue(counter.tryAcquire("1.2.3.4"), "3rd");
        assertFalse(counter.tryAcquire("1.2.3.4"), "4th is over the limit");
    }

    @Test
    void countsEachIdentitySeparately() {
        final FixedWindowCounter counter = new FixedWindowCounter(1);

        assertTrue(counter.tryAcquire("a"));
        assertFalse(counter.tryAcquire("a"));
        assertTrue(counter.tryAcquire("b"), "one key exhausting its window must not affect another");
    }

    @Test
    void windowAndMemoryBoundAreTheSharedPolicy() {
        assertEquals(60, FixedWindowCounter.WINDOW_SECONDS,
                "every limiter documents a per-minute window; the primitive owns that value");
        assertEquals(10_000L, FixedWindowCounter.MAX_KEYS,
                "the bound that keeps an identity-distributed flood from growing the cache");
    }
}
