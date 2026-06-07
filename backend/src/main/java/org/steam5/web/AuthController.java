package org.steam5.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.auth.SteamOpenIdUtils;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("https://steamcommunity.com/openid/id/([0-9]{17})");
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
        final String returnTo = (state != null && SAFE_STATE_PATTERN.matcher(state).matches())
                ? baseReturnTo + (baseReturnTo.contains("?") ? "&" : "?") + "state=" + SteamOpenIdUtils.enc(state)
                : baseReturnTo;

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

            // Hard verify Steam response: must contain is_valid:true
            if (res.statusCode() != 200 || resBody == null || !resBody.contains("is_valid:true")) {
                log.warn("Steam OpenID verification failed: status={} sample=\n{}", res.statusCode(),
                        resBody == null ? "<null>" : resBody.substring(0, Math.min(resBody.length(), 200)));
                return ResponseEntity.status(401).body(Map.of("error", "invalid_openid"));
            }

            // Extract SteamID64 from claimed_id
            final String claimed = params.get("openid.claimed_id");
            if (claimed == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_claimed_id"));
            }
            final Matcher m = STEAM_ID_PATTERN.matcher(claimed);
            if (!m.find()) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_claimed_id"));
            }
            final String steamId = m.group(1);

            // Enrich user profile (persona name) — runs asynchronously; does not block login
            steamUserService.updateUserProfile(steamId);

            // Issue signed token
            final String token = tokenService.generateToken(steamId);
            return ResponseEntity.ok(Map.of("steamId", steamId, "token", token));
        } catch (Exception e) {
            log.error("Steam OpenID callback error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "auth_failed"));
        }
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
        // Token validation is per-user and must never be stored by any cache.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("valid", true, "steamId", steamId));
    }
}
