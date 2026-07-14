package org.steam5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.steam5.web.ws.PresenceHandshakeInterceptor;
import org.steam5.web.ws.PresenceWebSocketHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceWebSocketHandler presenceHandler;
    private final PresenceHandshakeInterceptor handshakeInterceptor;

    /**
     * Registers the presence WebSocket endpoint and its handshake interceptor.
     *
     * @param registry the registry used to configure WebSocket handlers
     */
    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(presenceHandler, "/ws/presence")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(handshakeInterceptor.getAllowedOrigins().toArray(new String[0]));
    }
}
