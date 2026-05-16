package org.steam5.http;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class SteamRateLimiter {

    private volatile long nextAllowedEpochMs = 0L;
    private long minIntervalMs = 1000L;
    private final Timer waitTimer;

    public SteamRateLimiter(MeterRegistry meterRegistry) {
        this.waitTimer = Timer.builder("steam.api.rate.limiter.wait")
                .description("Time waited acquiring the Steam API rate-limit permit")
                .register(meterRegistry);
    }

    public synchronized void setMinInterval(Duration interval) {
        this.minIntervalMs = Math.max(0L, interval.toMillis());
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
            nextAllowedEpochMs = Math.max(now, nextAllowedEpochMs) + minIntervalMs;
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
