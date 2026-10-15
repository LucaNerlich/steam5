package org.steam5.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Parses the comma-separated origin list, trimming each entry and dropping blanks.
     *
     * <p>Both this filter and the presence WebSocket interceptor read the same property, so the
     * parse lives here and both call it: an untrimmed entry — `"https://a.org, https://b.org"`
     * is the natural way to write a list — can never equal an {@code Origin} header, so HTTP
     * CORS silently rejected an origin that the handshake still accepted, and the failure looked
     * like a frontend bug.</p>
     *
     * @param originsCsv the configured comma-separated origin list
     * @return the trimmed, non-empty entries
     */
    public static List<String> parseAllowedOrigins(final String originsCsv) {
        return Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsFilter corsFilter(
            @Value("${cors.allowedOrigins:https://steam5.org,https://next.steam5.org,http://localhost:3000}") String originsCsv
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(parseAllowedOrigins(originsCsv));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}


