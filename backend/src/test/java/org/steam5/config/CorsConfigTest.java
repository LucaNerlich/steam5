package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One parse for the {@code cors.allowedOrigins} property, shared by {@link CorsConfig}'s filter
 * and the presence handshake interceptor.
 *
 * <p>The defect this pins: the filter used the raw {@code split(",")} output, so an entry written
 * with a space after the comma — the natural way to write a list — could never equal an
 * {@code Origin} header. HTTP CORS then rejected an origin that the WebSocket handshake still
 * accepted, and the failure looked like a frontend bug.</p>
 */
class CorsConfigTest {

    @Test
    void trimsEntriesSoASpacedCsvStillMatchesAnOriginHeader() {
        assertEquals(List.of("https://steam5.org", "https://next.steam5.org"),
                CorsConfig.parseAllowedOrigins("https://steam5.org, https://next.steam5.org"));
    }

    @Test
    void dropsEmptyEntries() {
        assertEquals(List.of("https://steam5.org"),
                CorsConfig.parseAllowedOrigins(" https://steam5.org ,,  "));
        assertEquals(List.of(), CorsConfig.parseAllowedOrigins(""));
        assertEquals(List.of(), CorsConfig.parseAllowedOrigins("   "));
    }

    @Test
    void keepsASingleOriginUnchanged() {
        assertEquals(List.of("https://steam5.org"),
                CorsConfig.parseAllowedOrigins("https://steam5.org"));
    }

    @Test
    void exposesACorsConfigurationSourceAndNoStandaloneFilterBean() throws Exception {
        // Spring Security's CorsConfigurer looks up a bean named "corsConfigurationSource";
        // wiring it into the chain is what puts CORS ahead of authorization.
        final Method source = CorsConfig.class.getMethod("corsConfigurationSource", String.class);
        assertTrue(CorsConfigurationSource.class.isAssignableFrom(source.getReturnType()));
        assertTrue(source.isAnnotationPresent(Bean.class));

        // The standalone filter bean is deliberately gone: with no order it ran after the
        // security chain (order -100), which is the defect this pins shut.
        assertThrows(NoSuchMethodException.class, () -> CorsConfig.class.getMethod("corsFilter", String.class));
    }
}
