package org.steam5.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.steam5.security.AdminTokenFilter;
import org.steam5.security.PathRateLimitFilter;
import org.steam5.security.PresenceRateLimitFilter;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String DEFAULT_METRICS_CRED = "metrics";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager metricsUserDetailsService(
            @Value("${metrics.username:metrics}") String username,
            @Value("${metrics.password:metrics}") String password,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        // Gate on the credential itself, not on the both-defaults pair: a present-but-empty
        // METRICS_PASSWORD resolves to "" (it beats the placeholder default), and setting only
        // METRICS_USERNAME leaves the password at "metrics" — both used to pass as "overridden"
        // and start a production instance whose actuator surface anyone could scrape.
        final boolean defaultsOrBlank = !StringUtils.hasText(username)
                || !StringUtils.hasText(password)
                || DEFAULT_METRICS_CRED.equals(password);
        if (defaultsOrBlank) {
            final String[] activeProfiles = environment.getActiveProfiles();
            final boolean isDevProfile = Arrays.asList(activeProfiles).contains("dev");
            if (!isDevProfile) {
                // Default credentials in a non-dev profile let anyone scrape
                // /actuator/prometheus and satisfy basic auth on shared chains.
                throw new IllegalStateException(
                        "METRICS_USERNAME / METRICS_PASSWORD is blank, or METRICS_PASSWORD is still the " +
                        "default (\"metrics\") — refusing to start. Override METRICS_USERNAME / METRICS_PASSWORD " +
                        "via environment variables before exposing the actuator endpoints. (active profiles: " +
                        Arrays.toString(activeProfiles) + ")");
            }
            log.warn("Dev profile active but METRICS_USERNAME / METRICS_PASSWORD are blank or still the defaults — "
                    + "set them via environment variables before exposing /actuator/prometheus to the public.");
        }
        UserDetails metricsUser = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("METRICS")
                .build();
        return new InMemoryUserDetailsManager(metricsUser);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().hasRole("METRICS")
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Configures security for application endpoints, including public routes, authentication requirements,
     * HTTP Basic authentication, and custom security filters.
     *
     * @return the configured application security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AdminTokenFilter adminTokenFilter,
                                                   PathRateLimitFilter pathRateLimitFilter,
                                                   PresenceRateLimitFilter presenceRateLimitFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS inside the chain, before authorization: as a standalone CorsFilter bean it
                // ran *after* Spring Security's chain (order -100), so a preflight for any
                // authorization-protected path was denied before CORS headers could be added and
                // the filters' own OPTIONS exemptions were dead. See CorsConfig.
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/review-game/**").permitAll()
                        .requestMatchers("/api/details/**").permitAll()
                        // Operational data is gated behind the admin token (AdminTokenFilter);
                        // only the pick summary consumed by the public statistics UI stays open.
                        .requestMatchers("/api/metrics/picks/summary").permitAll()
                        .requestMatchers("/api/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/cache/**").hasRole("ADMIN")
                        .requestMatchers("/api/leaderboard/**").permitAll()
                        .requestMatchers("/api/profile/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/search").permitAll()
                        // AdminTokenFilter authenticates with the X-Admin-Token header and
                        // grants ROLE_ADMIN; plain basic auth (e.g. the metrics user) must not
                        // be able to reach admin operations.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/seasons/**").permitAll()
                        .requestMatchers("/api/stats/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ws/ticket").permitAll()
                        .requestMatchers("/ws/presence").permitAll()
                        // Nothing is served outside the matcher table above, and the only
                        // UserDetailsService in the context is the shared metrics scraper
                        // account. `authenticated()` + httpBasic therefore made that shared
                        // credential an app-chain principal for any future unmapped path;
                        // deny instead, so a missing matcher line fails closed.
                        .anyRequest().denyAll()
                )
                // Fix #5: both custom filters are anchored before BasicAuthenticationFilter.
                // The rate limiter is registered first so it runs before AdminTokenFilter —
                // addFilterBefore inserts each filter directly before the anchor, so the
                // first registration ends up earliest in the chain.
                .addFilterBefore(pathRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(presenceRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(adminTokenFilter, BasicAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}


