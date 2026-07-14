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
import org.steam5.service.PresenceMetrics;
import org.steam5.service.PresenceRateLimiter;
import org.steam5.service.RoundPresenceService;

import java.net.URI;
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

    private final List<String> allowedOrigins;
    private final PresenceRateLimiter presenceRateLimiter;
    private final PresenceMetrics presenceMetrics;

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
            log.warn("Rejecting presence handshake — rate limit exceeded for ip={}", clientIp);
            presenceMetrics.recordHandshakeRejected();
            return false;
        }

        final URI uri = request.getURI();
        final String query = uri.getRawQuery();
        final String scopeKey = extractParam(query, "scopeKey");
        final String ticket = extractParam(query, "ticket");

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

    private boolean isAllowed(final String origin) {
        for (final String allowed : allowedOrigins) {
            if (allowed.equals(origin) || "*".equals(allowed)) return true;
        }
        return false;
    }

    private static String resolveClientIp(final ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRemoteAddr();
        }
        return null;
    }

    static String extractParam(final String query, final String name) {
        if (query == null || query.isEmpty()) return null;
        for (final String pair : query.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) continue;
            final String key = pair.substring(0, eq);
            if (!name.equals(key)) continue;
            final String rawValue = pair.substring(eq + 1);
            try {
                return java.net.URLDecoder.decode(rawValue, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
