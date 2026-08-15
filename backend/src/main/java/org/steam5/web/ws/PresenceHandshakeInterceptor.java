package org.steam5.web.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.service.PresenceMetrics;
import org.steam5.service.PresenceRateLimiter;
import org.steam5.service.RoundPresenceService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates the {@code Origin} header, enforces handshake rate limits, and copies
 * handshake query parameters into WebSocket session attributes.
 */
@Slf4j
@Component
public class PresenceHandshakeInterceptor implements HandshakeInterceptor {

    public static final Pattern SCOPE_KEY_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(:\\d+:\\d+)?$");

    /**
     * Subprotocol prefix used to pass the presence ticket during the WebSocket
     * handshake instead of a URL query parameter, keeping the ticket out of
     * access logs and proxy log pipelines.
     */
    public static final String TICKET_SUBPROTOCOL_PREFIX = "s5ticket.";

    private final List<String> allowedOrigins;
    private final PresenceRateLimiter presenceRateLimiter;
    private final PresenceMetrics presenceMetrics;

    /**
     * Creates an interceptor configured with the allowed handshake origins.
     *
     * @param originsCsv comma-separated list of allowed origins
     */
    public PresenceHandshakeInterceptor(
            @Value("${cors.allowedOrigins:https://steam5.org,https://next.steam5.org,http://localhost:3000}") final String originsCsv,
            final PresenceRateLimiter presenceRateLimiter,
            final PresenceMetrics presenceMetrics) {
        this.allowedOrigins = Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.presenceRateLimiter = presenceRateLimiter;
        this.presenceMetrics = presenceMetrics;
    }

    /**
     * Validates and prepares a presence WebSocket handshake.
     *
     * @param request    the handshake request to validate
     * @param attributes the WebSocket session attributes to populate on success
     * @return {@code true} if the handshake is allowed, {@code false} otherwise
     */
    @Override
    public boolean beforeHandshake(final ServerHttpRequest request,
                                   final ServerHttpResponse response,
                                   final WebSocketHandler wsHandler,
                                   final Map<String, Object> attributes) {
        final String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin == null || !isAllowed(origin)) {
            log.debug("Rejecting presence handshake — origin '{}' not in allow-list", origin);
            presenceMetrics.recordHandshakeRejected();
            return false;
        }

        final String clientIp = resolveClientIp(request);
        if (!presenceRateLimiter.tryAcquireHandshake(clientIp)) {
            log.warn("Rejecting presence handshake — rate limit exceeded for clientRef={}",
                    loggableClientRef(clientIp));
            presenceMetrics.recordHandshakeRejected();
            return false;
        }

        final URI uri = request.getURI();
        final String query = uri.getRawQuery();
        final String scopeKey = extractParam(query, "scopeKey");
        String ticket = extractParam(query, "ticket");

        // The client may pass the ticket as a WebSocket subprotocol instead of a URL
        // query parameter so it does not leak into access logs. When offered, echo the
        // selected subprotocol back so the handshake completes. The query parameter
        // takes precedence for backward compatibility.
        if (ticket == null || ticket.isBlank()) {
            final String selected = ticketSubprotocol(request.getHeaders().getFirst("Sec-WebSocket-Protocol"));
            if (selected != null) {
                response.getHeaders().set("Sec-WebSocket-Protocol", selected);
                ticket = selected.substring(TICKET_SUBPROTOCOL_PREFIX.length());
            }
        }

        if (scopeKey == null || scopeKey.isBlank()) {
            log.debug("Rejecting presence handshake — missing scopeKey");
            presenceMetrics.recordHandshakeRejected();
            return false;
        }

        if (!SCOPE_KEY_PATTERN.matcher(scopeKey).matches()) {
            log.debug("Rejecting presence handshake — malformed scopeKey '{}'", scopeKey);
            presenceMetrics.recordHandshakeRejected();
            return false;
        }

        attributes.put(RoundPresenceService.ATTR_SCOPE_KEY, scopeKey);
        attributes.put(RoundPresenceService.ATTR_CLIENT_IP, clientIp);
        if (ticket != null && !ticket.isBlank()) {
            attributes.put("ticket", ticket);
        }
        return true;
    }

    @Override
    public void afterHandshake(final ServerHttpRequest request,
                               final ServerHttpResponse response,
                               final WebSocketHandler wsHandler,
                               final Exception exception) {
        // no-op
    }

    /**
     * Provides the origins permitted for WebSocket handshakes.
     *
     * @return the configured allowed origins
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    private boolean isAllowed(final String origin) {
        return allowedOrigins.contains(origin);
    }

    /**
     * Creates a short, privacy-preserving reference for a client IP address.
     *
     * @param clientIp the client IP address to reference
     * @return an {@code ipHash:} value based on the first four bytes of the SHA-256 digest,
     *         or {@code unknown} when the address is blank or hashing is unavailable
     */
    static String loggableClientRef(final String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(clientIp.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(16);
            sb.append("ipHash:");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * Resolves the remote client IP address from a servlet-backed request.
     *
     * @param request the HTTP request
     * @return the remote client IP address, or {@code null} when the request is not servlet-backed
     */
    private static String resolveClientIp(final ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRemoteAddr();
        }
        return null;
    }

    /**
     * Extracts the first value associated with a query parameter name.
     *
     * @param query the raw query string
     * @param name  the query parameter name
     * @return the first parameter value, or {@code null} when the query is empty or the parameter has no values
     */
    static String extractParam(final String query, final String name) {
        if (query == null || query.isEmpty()) return null;
        final List<String> values = UriComponentsBuilder.newInstance()
                .query(query)
                .build()
                .getQueryParams()
                .get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Finds the offered WebSocket subprotocol that carries the presence ticket.
     *
     * @param subprotocolHeader the raw {@code Sec-WebSocket-Protocol} header value
     * @return the full matched subprotocol token (e.g. {@code s5ticket.eyJ...}), or
     *         {@code null} when the header is absent or carries no ticket subprotocol
     */
    static String ticketSubprotocol(final String subprotocolHeader) {
        if (subprotocolHeader == null) return null;
        for (final String candidate : subprotocolHeader.split(",")) {
            final String value = candidate.trim();
            if (value.startsWith(TICKET_SUBPROTOCOL_PREFIX) && value.length() > TICKET_SUBPROTOCOL_PREFIX.length()) {
                return value;
            }
        }
        return null;
    }
}
