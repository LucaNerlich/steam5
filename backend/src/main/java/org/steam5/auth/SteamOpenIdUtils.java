package org.steam5.auth;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Pure utility functions for the Steam OpenID 2.0 protocol. Extracted from
 * {@link org.steam5.web.AuthController} so they can be unit-tested without
 * loading the full Spring context, and reused by any future auth endpoint.
 *
 * <p>All methods are stateless and have no Spring dependencies.</p>
 */
public final class SteamOpenIdUtils {

    private SteamOpenIdUtils() {}

    /** URL-encodes {@code v} using UTF-8. */
    public static String enc(final String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * Builds the {@code check_authentication} POST body required by the OpenID
     * verification step. Copies all {@code openid.*} parameters from the
     * assertion callback (except {@code openid.mode}), then sets
     * {@code openid.mode=check_authentication}.
     *
     * @param params raw query-string parameters from the Steam callback
     * @return URL-encoded form body ready to POST to the Steam OpenID endpoint
     */
    public static String buildCheckAuthBody(final Map<String, String> params) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (final Map.Entry<String, String> e : params.entrySet()) {
            final String key = e.getKey();
            if (key.startsWith("openid.") && !"openid.mode".equals(key)) {
                form.add(key, e.getValue());
            }
        }
        form.add("openid.mode", "check_authentication");
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final Map.Entry<String, List<String>> e : form.entrySet()) {
            for (final String v : e.getValue()) {
                if (!first) sb.append('&');
                sb.append(enc(e.getKey())).append('=').append(enc(v));
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Extracts the {@code scheme://host[:port]} origin from {@code url},
     * returning {@code fallback} if the URL is unparseable.
     */
    public static String deriveOriginSafe(final String url, final String fallback) {
        try {
            final URI r = URI.create(url);
            final StringBuilder origin = new StringBuilder()
                    .append(r.getScheme()).append("://").append(r.getHost());
            if (r.getPort() != -1) origin.append(':').append(r.getPort());
            return origin.toString();
        } catch (final Exception e) {
            return fallback;
        }
    }

    /**
     * Returns {@code true} when {@code redirect} shares the same scheme, host,
     * and port as {@code allowedBase}, preventing open-redirect attacks.
     */
    public static boolean isAllowedRedirect(final String redirect, final String allowedBase) {
        try {
            final URI uri = URI.create(redirect);
            final URI base = URI.create(allowedBase);
            return uri.getScheme() != null
                    && uri.getScheme().equals(base.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().equals(base.getHost())
                    && uri.getPort() == base.getPort();
        } catch (final Exception e) {
            return false;
        }
    }
}
