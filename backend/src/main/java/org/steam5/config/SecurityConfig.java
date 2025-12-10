package org.steam5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.steam5.security.AdminTokenFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminTokenFilter adminTokenFilter) {
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
                .addFilterBefore(adminTokenFilter, BasicAuthenticationFilter.class)
                .httpBasic(basic -> {
                })
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}


