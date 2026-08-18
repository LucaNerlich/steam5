package org.steam5.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side one-time nonces for the Steam OpenID login flow.
 *
 * <p>Steam's OpenID 2.0 assertion is a signed URL with no built-in nonce, so a
 * captured callback URL could be replayed to obtain fresh tokens. {@code /steam/login}
 * issues a nonce and embeds it in {@code openid.return_to}; the callback must
 * present and consume it exactly once. The store also records the exact
 * return-to base URL built at login time so the callback can verify Steam
 * echoed the same URL back.</p>
 */
@Component
public class OpenIdNonceStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String returnToBase, long expiresAtEpochMillis) {
    }

    private final ConcurrentHashMap<String, Entry> nonces = new ConcurrentHashMap<>();

    /**
     * Issues a fresh nonce bound to the given return-to base URL
     * ({@code scheme://host[:port]/path}, without query).
     */
    public String issue(String returnToBase) {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        final String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (nonces.size() >= MAX_ENTRIES) {
            removeExpired();
            if (nonces.size() >= MAX_ENTRIES) {
                // Defensive bound: drop an arbitrary entry rather than grow unbounded.
                nonces.keySet().stream().findAny().ifPresent(nonces::remove);
            }
        }
        nonces.put(nonce, new Entry(returnToBase, System.currentTimeMillis() + TTL.toMillis()));
        return nonce;
    }

    /**
     * Consumes the nonce (single use). Returns the return-to base URL bound at
     * issue time, or {@code null} when the nonce is unknown, expired, or already used.
     */
    public String consume(String nonce) {
        if (nonce == null) return null;
        final Entry entry = nonces.remove(nonce);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAtEpochMillis()) return null;
        return entry.returnToBase();
    }

    private void removeExpired() {
        final long now = System.currentTimeMillis();
        nonces.entrySet().removeIf(e -> now > e.getValue().expiresAtEpochMillis());
    }
}
