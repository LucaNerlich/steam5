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

    /**
     * Creates the presence metrics and registers them with the specified meter registry.
     *
     * @param meterRegistry   the registry used to register presence metrics
     * @param presenceService the service that provides the active connection count
     */
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

    /**
     * Records a rejected presence WebSocket handshake.
     */
    public void recordHandshakeRejected() {
        handshakeRejected.increment();
    }

    /**
     * Records a failed presence snapshot broadcast.
     */
    public void recordBroadcastFailure() {
        broadcastFailures.increment();
    }

    /**
     * Records the issuance of a WebSocket ticket.
     */
    public void recordTicketIssued() {
        ticketsIssued.increment();
    }

    /**
     * Records an invalid or expired WebSocket ticket presented during a handshake.
     */
    public void recordTicketInvalid() {
        ticketsInvalid.increment();
    }

    /**
     * Records a presence session closed due to inactivity.
     */
    public void recordIdleTimeout() {
        idleTimeouts.increment();
    }

    /**
     * Records a rejected presence session registration after the WebSocket handshake.
     *
     * @param reason the reason the registration was rejected
     */
    public void recordRegistrationRejected(final String reason) {
        Counter.builder("presence.registration.rejected")
                .description("Presence session registrations rejected after handshake")
                .tag("application", "steam5")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}
