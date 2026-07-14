package org.steam5.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for the round-presence WebSocket feature.
 */
@Component
public class PresenceMetrics {

    private final Counter handshakeRejected;
    private final Counter broadcastFailures;
    private final Counter ticketsIssued;
    private final Counter ticketsInvalid;
    private final Counter idleTimeouts;
    private final MeterRegistry meterRegistry;

    public PresenceMetrics(final MeterRegistry meterRegistry, @Lazy final RoundPresenceService presenceService) {
        this.meterRegistry = meterRegistry;
        this.handshakeRejected = Counter.builder("presence.handshake.rejected")
                .description("Presence WebSocket handshakes rejected")
                .tag("application", "steam5")
                .register(meterRegistry);
        this.broadcastFailures = Counter.builder("presence.broadcast.failures")
                .description("Failed presence snapshot broadcasts to a session")
                .tag("application", "steam5")
                .register(meterRegistry);
        this.ticketsIssued = Counter.builder("presence.ticket.issued")
                .description("Short-lived WebSocket tickets issued")
                .tag("application", "steam5")
                .register(meterRegistry);
        this.ticketsInvalid = Counter.builder("presence.ticket.invalid")
                .description("Invalid or expired WebSocket tickets presented at handshake")
                .tag("application", "steam5")
                .register(meterRegistry);
        this.idleTimeouts = Counter.builder("presence.session.idle_timeout")
                .description("Presence sessions closed due to inactivity")
                .tag("application", "steam5")
                .register(meterRegistry);

        Gauge.builder("presence.connections.active", presenceService::activeConnectionCount)
                .description("Active presence WebSocket connections")
                .tag("application", "steam5")
                .register(meterRegistry);
    }

    public void recordHandshakeRejected() {
        handshakeRejected.increment();
    }

    public void recordBroadcastFailure() {
        broadcastFailures.increment();
    }

    public void recordTicketIssued() {
        ticketsIssued.increment();
    }

    public void recordTicketInvalid() {
        ticketsInvalid.increment();
    }

    public void recordIdleTimeout() {
        idleTimeouts.increment();
    }

    public void recordRegistrationRejected(final String reason) {
        Counter.builder("presence.registration.rejected")
                .description("Presence session registrations rejected after handshake")
                .tag("application", "steam5")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}
