package org.steam5.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.steam5.service.RoundPresenceService;
import org.steam5.web.ws.PresenceWebSocketHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceWebSocketHandler presenceHandler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            final PresenceWebSocketHandler presenceHandler,
            @Value("${cors.allowedOrigins:https://steam5.org,https://next.steam5.org,http://localhost:3000}") final String originsCsv
    ) {
        this.presenceHandler = presenceHandler;
        this.allowedOrigins = Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(presenceHandler, "/ws/presence")
                .addInterceptors(new PresenceHandshakeInterceptor(allowedOrigins))
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]));
    }

    /**
     * Validates the {@code Origin} header against the configured allow-list and
     * copies the {@code scopeKey} and {@code ticket} query parameters into the
     * WebSocket session attributes so the handler can read them at connection time.
     */
    static class PresenceHandshakeInterceptor implements HandshakeInterceptor {

        private final List<String> allowedOrigins;

        PresenceHandshakeInterceptor(final List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        @Override
        public boolean beforeHandshake(final ServerHttpRequest request,
                                       final ServerHttpResponse response,
                                       final WebSocketHandler wsHandler,
                                       final Map<String, Object> attributes) {
            final String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
            if (origin == null || !isAllowed(origin)) {
                log.debug("Rejecting presence handshake — origin '{}' not in allow-list", origin);
                return false;
            }

            final URI uri = request.getURI();
            final String query = uri.getRawQuery();
            final String scopeKey = extractParam(query, "scopeKey");
            final String ticket = extractParam(query, "ticket");

            if (scopeKey == null || scopeKey.isBlank()) {
                log.debug("Rejecting presence handshake — missing scopeKey");
                return false;
            }

            attributes.put(RoundPresenceService.ATTR_SCOPE_KEY, scopeKey);
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

        private static String extractParam(final String query, final String name) {
            if (query == null || query.isEmpty()) return null;
            for (final String pair : query.split("&")) {
                final int eq = pair.indexOf('=');
                if (eq < 0) continue;
                final String key = pair.substring(0, eq);
                if (!name.equals(key)) continue;
                final String rawValue = pair.substring(eq + 1);
                return java.net.URLDecoder.decode(rawValue, java.nio.charset.StandardCharsets.UTF_8);
            }
            return null;
        }
    }
}
