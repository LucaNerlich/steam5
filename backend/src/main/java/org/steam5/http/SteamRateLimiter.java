package org.steam5.http;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SteamRateLimiter {

    private volatile long nextAllowedEpochMs = 0L;
    /** Minimum spacing between Steam API calls. */
    private static final long MIN_INTERVAL_MS = 1000L;
    private final Timer waitTimer;

    public SteamRateLimiter(MeterRegistry meterRegistry) {
        this.waitTimer = Timer.builder("steam.api.rate.limiter.wait")
                .description("Time waited acquiring the Steam API rate-limit permit")
                .register(meterRegistry);
    }

    public void acquirePermit() {
        long now = System.currentTimeMillis();
        long waitMs;
        synchronized (this) {
            if (now < nextAllowedEpochMs) {
                waitMs = nextAllowedEpochMs - now;
            } else {
                waitMs = 0L;
            }
            nextAllowedEpochMs = Math.max(now, nextAllowedEpochMs) + MIN_INTERVAL_MS;
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
