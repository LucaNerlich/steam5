package org.steam5.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.auth.OpenIdNonceStore;
import org.steam5.auth.SteamOpenIdUtils;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Validated
@Slf4j
public class AuthController {

    private static final String OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("https://steamcommunity\\.com/openid/id/([0-9]{17})");
    // Allowlist for the state/nonce param: alphanumeric + hyphens/underscores, 8–128 chars
    private static final Pattern SAFE_STATE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,128}");

    // Fix #9: reuse a single HttpClient across all callback requests instead of
    // constructing one per login (which discards the internal thread/connection pool).
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final AuthTokenService tokenService;
    private final SteamUserService steamUserService;
    private final UserRepository userRepository;
    private final OpenIdNonceStore nonceStore;

    @Value("${auth.redirectBase:https://steam5.org}")
    private String defaultRedirectBase;

    @GetMapping("/steam/login")
    public ResponseEntity<Void> startLogin(
            @RequestParam(value = "redirect", required = false) String redirect,
            @RequestParam(value = "state", required = false) String state) {

        // Fix #2: validate redirect against the trusted frontend origin; fall back
        // to the configured default if the supplied value is absent or untrusted.
        final String baseReturnTo = (redirect == null || redirect.isBlank() || !SteamOpenIdUtils.isAllowedRedirect(redirect, defaultRedirectBase))
                ? defaultRedirectBase + "/api/auth/steam/callback"
                : redirect;

        // Fix #6: if the frontend supplied a CSRF state token, embed it in the
        // return_to URL so Steam carries it back in the redirect, and the
        // callback can verify it against the browser's cookie.
        final String withState = (state != null && SAFE_STATE_PATTERN.matcher(state).matches())
                ? baseReturnTo + (baseReturnTo.contains("?") ? "&" : "?") + "state=" + SteamOpenIdUtils.enc(state)
                : baseReturnTo;

        // Fix #10: bind a server-side single-use nonce to the exact return-to URL
        // this login builds. The callback consumes it and verifies the echoed
        // openid.return_to matches, closing the assertion-replay window.
        final String returnTo = withState + (withState.contains("?") ? "&" : "?") + "nonce=" + SteamOpenIdUtils.enc(nonceStore.issue(baseOf(withState)));

        final String realm = SteamOpenIdUtils.deriveOriginSafe(returnTo, defaultRedirectBase);
        final String url = OPENID_ENDPOINT + "?openid.ns=" + SteamOpenIdUtils.enc("http://specs.openid.net/auth/2.0")
                + "&openid.mode=checkid_setup"
                + "&openid.return_to=" + SteamOpenIdUtils.enc(returnTo)
                + "&openid.realm=" + SteamOpenIdUtils.enc(realm)
                + "&openid.identity=" + SteamOpenIdUtils.enc("http://specs.openid.net/auth/2.0/identifier_select")
                + "&openid.claimed_id=" + SteamOpenIdUtils.enc("http://specs.openid.net/auth/2.0/identifier_select");
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/steam/callback")
    public ResponseEntity<?> callback(@RequestParam Map<String, String> params) {
        // Hard verify the assertion with Steam first: the response must contain
        // is_valid:true.
        if (!steamVerifies(params)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_openid"));
        }

        // Extract SteamID64 from claimed_id, and hard-verify the assertion's
        // internal consistency: claimed_id must be signed by Steam and match
        // openid.identity (otherwise a tampered/unsafe claimed_id could be
        // accepted via the unanchored match).
        final String claimed = params.get("openid.claimed_id");
        final String identity = params.get("openid.identity");
        final String signedFields = params.get("openid.signed");
        if (claimed == null || identity == null || signedFields == null
                || !Arrays.stream(signedFields.split(",")).map(String::trim).anyMatch("claimed_id"::equals)
                || !claimed.equals(identity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_claimed_id"));
        }
        final Matcher m = STEAM_ID_PATTERN.matcher(claimed);
        if (!m.matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_claimed_id"));
        }
        final String steamId = m.group(1);

        // Replay protection: consume the single-use nonce issued at login time
        // and require Steam to echo back the exact return-to URL we built.
        final String returnTo = params.get("openid.return_to");
        final String nonce = returnTo == null ? null : queryParam(returnTo, "nonce");
        final String expectedReturnToBase = nonceStore.consume(nonce);
        if (expectedReturnToBase == null || !expectedReturnToBase.equals(baseOf(returnTo))) {
            log.warn("Steam OpenID callback: nonce missing/expired or return_to mismatch (replay attempt?)");
            return ResponseEntity.status(401).body(Map.of("error", "invalid_openid"));
        }

        // Enrich user profile (persona name) — runs asynchronously; does not block login
        steamUserService.updateUserProfile(steamId);

        // Issue signed token. The response must never be cached — a shared cache
        // could serve one user's fresh token (or success payload) to another.
        final String token = tokenService.generateToken(steamId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("steamId", steamId, "token", token));
    }

    /**
     * POSTs the assertion back to the hardcoded Steam endpoint (never the
     * client-supplied openid.op_endpoint) and returns true only when Steam
     * answers {@code is_valid:true}. Extracted for unit-testability.
     */
    boolean steamVerifies(Map<String, String> params) {
        try {
            final String body = SteamOpenIdUtils.buildCheckAuthBody(params);

            // Fix #1: ALWAYS send the check_authentication request to the hardcoded
            // Steam endpoint.  Never trust the client-supplied openid.op_endpoint —
            // doing so would allow an attacker to point the backend at an arbitrary
            // server that returns is_valid:true (SSRF + authentication bypass).
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OPENID_ENDPOINT))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .header(HttpHeaders.ACCEPT, "text/plain")
                    .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                    .header(HttpHeaders.USER_AGENT, "steam5-auth/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            final HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            final String resBody = res.body();
            if (res.statusCode() / 100 == 3) {
                final String location = res.headers().firstValue("Location").orElse("<none>");
                log.warn("Steam OpenID verification redirected: status={} location={}", res.statusCode(), location);
            }

            if (res.statusCode() != 200 || resBody == null || !resBody.contains("is_valid:true")) {
                log.warn("Steam OpenID verification failed: status={} sample=\n{}", res.statusCode(),
                        resBody == null ? "<null>" : resBody.substring(0, Math.min(resBody.length(), 200)));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Steam OpenID verification request failed", e);
            return false;
        }
    }

    /** Returns {@code scheme://host[:port]/path} of a URL, or null when unparseable. */
    static String baseOf(String url) {
        if (url == null) return null;
        try {
            final URI uri = new URI(url);
            final StringBuilder base = new StringBuilder()
                    .append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) base.append(':').append(uri.getPort());
            base.append(uri.getPath());
            return base.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Extracts a query parameter value from a URL, or null when absent. */
    static String queryParam(String url, String name) {
        if (url == null) return null;
        final int q = url.indexOf('?');
        if (q < 0) return null;
        final String query = url.substring(q + 1);
        for (String pair : query.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) continue;
            if (name.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /**
     * Fix #4: Accept the JWT via {@code Authorization: Bearer} header rather than
     * a query parameter.  Tokens in query strings end up in server access logs,
     * browser history, and {@code Referer} headers — none of which is appropriate
     * for a credential.
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }
        final String token = authHeader.substring(7);
        final String steamId = tokenService.verifyToken(token);
        if (steamId == null) return ResponseEntity.status(401).body(Map.of("valid", false));
        // Surface the user's small Steam avatar so the header can render it alongside
        // "Profile" without an extra request. Use the small avatar (not the *Full
        // variants) since the header image is tiny. HashMap tolerates null fields.
        final Map<String, Object> body = new HashMap<>();
        body.put("valid", true);
        body.put("steamId", steamId);
        final User user = userRepository.findById(steamId).orElse(null);
        if (user != null) {
            body.put("avatar", user.getAvatar());
        }
        // Token validation is per-user and must never be stored by any cache.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
