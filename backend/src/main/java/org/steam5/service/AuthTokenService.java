package org.steam5.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class AuthTokenService {

    private final SecretKey key;

    public AuthTokenService(@Value("${auth.jwtSecret:change-me-please-change-me-32-bytes-min}") String secret,
                            Environment environment) {
        // Fix #8: enforce a minimum key length at startup so a misconfigured or
        // default secret causes an immediate, obvious failure rather than silently
        // running with a weak key in production.
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "auth.jwtSecret must be at least 32 characters. " +
                    "Generate a strong secret with: openssl rand -base64 48");
        }
        if (secret.startsWith("change-me")) {
            final String[] activeProfiles = environment.getActiveProfiles();
            final boolean isDevProfile = Arrays.asList(activeProfiles).contains("dev");
            if (!isDevProfile) {
                // The fallback secret is public knowledge; running with it in any
                // non-dev profile lets anyone forge tokens for arbitrary steamIds.
                throw new IllegalStateException(
                        "auth.jwtSecret is the publicly known default value — refusing to start. " +
                        "Set a strong random secret via the AUTH_JWT_SECRET environment variable. " +
                        "(active profiles: " + Arrays.toString(activeProfiles) + ")");
            }
            log.warn("auth.jwtSecret appears to be the default insecure value. " +
                     "Set a strong random secret in production via the AUTH_JWT_SECRET environment variable.");
        }
        // Derive HMAC key from provided secret.
        // Key length determines algorithm: ≥32 bytes → HS256, ≥48 → HS384, ≥64 → HS512
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateToken(String steamId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(60L * 60L * 24L * 30L); // 30 days
        return Jwts.builder()
                .subject(steamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public String verifyToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            // Debug level since this happens on every request with invalid/expired tokens
            log.debug("Token verification failed", e);
            return null;
        }
    }
}


