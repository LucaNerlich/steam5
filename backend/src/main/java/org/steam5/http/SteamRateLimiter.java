package org.steam5.http;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SteamRateLimiter {

    // Monotonic: a system-clock correction must not turn into a multi-minute sleep (backwards
    // step) or a burst at Steam (forwards step).
    // Long.MIN_VALUE, not 0: nanoTime may be negative, so 0 is not a safe "no deadline" sentinel.
    private volatile long nextAllowedNanos = Long.MIN_VALUE;
    /** Minimum spacing between Steam API calls. */
    private static final long MIN_INTERVAL_MS = 1000L;
    private final Timer waitTimer;

    public SteamRateLimiter(MeterRegistry meterRegistry) {
        this.waitTimer = Timer.builder("steam.api.rate.limiter.wait")
                .description("Time waited acquiring the Steam API rate-limit permit")
                .register(meterRegistry);
    }

    public void acquirePermit() {
        final long now = System.nanoTime();
        final long waitMs;
        synchronized (this) {
            waitMs = now < nextAllowedNanos
                    ? TimeUnit.NANOSECONDS.toMillis(nextAllowedNanos - now)
                    : 0L;
            nextAllowedNanos = Math.max(now, nextAllowedNanos) + TimeUnit.MILLISECONDS.toNanos(MIN_INTERVAL_MS);
        }
        if (waitMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(waitMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        waitTimer.record(waitMs, TimeUnit.MILLISECONDS);
    }
}
