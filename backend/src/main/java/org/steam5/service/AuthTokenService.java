package org.steam5.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class AuthTokenService {

    private final SecretKey key;

    public AuthTokenService(@Value("${auth.jwtSecret:change-me-please-change-me-32-bytes-min}") String secret) {
        // Derive HMAC key from provided secret
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


