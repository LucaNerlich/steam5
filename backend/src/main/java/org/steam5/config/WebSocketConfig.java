package org.steam5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
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

    /**
     * Caps Tomcat WebSocket buffer sizes. Presence only exchanges small JSON ping/snapshots;
     * large defaults waste per-session heap on small Coolify containers.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        final ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8 * 1024);
        container.setMaxBinaryMessageBufferSize(8 * 1024);
        // Align with presence.idle-timeout-seconds (default 90); container-level idle is a backstop.
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }
}
