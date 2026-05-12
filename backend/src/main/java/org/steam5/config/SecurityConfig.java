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
import org.steam5.security.AdminTokenFilter;
import org.steam5.security.AuthRateLimitFilter;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String DEFAULT_METRICS_CRED = "metrics";
    private static final String COOLIFY_PROFILE = "coolify";

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
        if (Arrays.asList(environment.getActiveProfiles()).contains(COOLIFY_PROFILE)
                && DEFAULT_METRICS_CRED.equals(username)
                && DEFAULT_METRICS_CRED.equals(password)) {
            log.warn("Coolify profile is active but METRICS_USERNAME / METRICS_PASSWORD are still the defaults — "
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().hasRole("METRICS")
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AdminTokenFilter adminTokenFilter,
                                                   AuthRateLimitFilter authRateLimitFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/review-game/**").permitAll()
                        .requestMatchers("/api/details/**").permitAll()
                        .requestMatchers("/api/metrics/**").permitAll()
                        .requestMatchers("/api/cache/**").permitAll()
                        .requestMatchers("/api/leaderboard/**").permitAll()
                        .requestMatchers("/api/profile/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/seasons/**").permitAll()
                        .requestMatchers("/api/stats/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Fix #5: both custom filters are anchored before BasicAuthenticationFilter.
                // The rate limiter is registered first so it runs before AdminTokenFilter —
                // addFilterBefore inserts each filter directly before the anchor, so the
                // first registration ends up earliest in the chain.
                .addFilterBefore(authRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(adminTokenFilter, BasicAuthenticationFilter.class)
                .httpBasic(basic -> {
                })
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}


