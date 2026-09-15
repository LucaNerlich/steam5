package org.steam5.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class AuthTokenService {

    private final SecretKey key;
    private final UserRepository userRepository;

    public AuthTokenService(@Value("${auth.jwtSecret:change-me-please-change-me-32-bytes-min}") String secret,
                            Environment environment,
                            UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return generateToken(steamId, Instant.now());
    }

    String generateToken(String steamId, Instant now) {
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
            final var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            final String steamId = claims.getSubject();
            // Reject tokens issued before the user's last logout, even though
            // signature and expiry are otherwise still valid. Missing user or a
            // null tokenNotValidBefore means the token was never invalidated.
            final User user = userRepository.findById(steamId).orElse(null);
            if (user != null && user.getTokenNotValidBefore() != null
                    && claims.getIssuedAt().toInstant().isBefore(
                            user.getTokenNotValidBefore().toInstant().truncatedTo(ChronoUnit.SECONDS))) {
                return null;
            }
            return steamId;
        } catch (Exception e) {
            // Debug level since this happens on every request with invalid/expired tokens
            log.debug("Token verification failed", e);
            return null;
        }
    }

    /** Invalidates every JWT issued before now for this user (called on logout). */
    @Transactional
    public void invalidateTokensIssuedBefore(String steamId, OffsetDateTime instant) {
        // JWT NumericDate claims have whole-second precision. Persist the same
        // precision so a new login later in this second is not mistaken for an
        // older token. The conditional repository update is a single statement,
        // so concurrent logouts can only advance this cutoff.
        final OffsetDateTime cutoff = instant.withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        userRepository.advanceTokenNotValidBefore(steamId, cutoff);
    }
}

