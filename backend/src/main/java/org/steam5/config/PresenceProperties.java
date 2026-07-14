package org.steam5.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "presence")
public class PresenceProperties {

    /** Maximum concurrent presence WebSocket sessions across all scopes. */
    private int maxGlobalConnections = 5_000;

    /** Maximum concurrent sessions for a single scope (e.g. one game day). */
    private int maxScopeConnections = 500;

    /** Maximum concurrent sessions from a single client IP. */
    private int maxIpConnections = 20;

    /** Per-IP ticket issuance limit per minute. */
    private int ticketRateLimitPerMinute = 20;

    /** Per-authenticated-user ticket issuance limit per minute. */
    private int ticketRateLimitPerUserPerMinute = 10;

    /** Per-IP WebSocket handshake limit per minute. */
    private int handshakeRateLimitPerMinute = 60;

    /** Close sessions with no ping/pong activity for this many seconds. */
    private int idleTimeoutSeconds = 90;

    /** How often to sweep for closed or idle sessions (milliseconds). */
    private long sweepIntervalMs = 30_000L;
}
