package org.steam5.http;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class SteamRateLimiter {

    private volatile long nextAllowedEpochMs = 0L;
    private long minIntervalMs = 1000L;

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
    }
}


