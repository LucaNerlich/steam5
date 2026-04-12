package org.steam5.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * Fix #2: Validate that the supplied redirect URL belongs to the trusted
     * frontend origin.  Accepts only URLs whose scheme, host, and port all match
     * {@code defaultRedirectBase}.  Rejects everything else, falling back to the
     * hard-coded default callback URL.
     */
    private boolean isAllowedRedirect(String redirect) {
        try {
            URI uri = URI.create(redirect);
            URI base = URI.create(defaultRedirectBase);
            return uri.getScheme() != null
                    && uri.getScheme().equals(base.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().equals(base.getHost())
                    && uri.getPort() == base.getPort();
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildCheckAuthBody(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Copy back all openid.* assertion params EXCEPT mode
        for (Map.Entry<String, String> e : params.entrySet()) {
            final String key = e.getKey();
            if (key.startsWith("openid.") && !"openid.mode".equals(key)) {
                form.add(key, e.getValue());
            }
        }
        // Replace mode with check_authentication as required by OpenID spec
        form.add("openid.mode", "check_authentication");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> e : form.entrySet()) {
            for (String v : e.getValue()) {
                if (!first) sb.append('&');
                sb.append(enc(e.getKey())).append('=').append(enc(v));
                first = false;
            }
        }
        return sb.toString();
    }

    private static String deriveOriginSafe(String url, String fallback) {
        try {
            URI r = URI.create(url);
            StringBuilder origin = new StringBuilder();
            origin.append(r.getScheme()).append("://").append(r.getHost());
            if (r.getPort() != -1) origin.append(":").append(r.getPort());
            return origin.toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    @GetMapping("/steam/login")
    public ResponseEntity<Void> startLogin(
            @RequestParam(value = "redirect", required = false) String redirect,
            @RequestParam(value = "state", required = false) String state) {

        // Fix #2: validate redirect against the trusted frontend origin; fall back
        // to the configured default if the supplied value is absent or untrusted.
        final String baseReturnTo = (redirect == null || redirect.isBlank() || !isAllowedRedirect(redirect))
                ? defaultRedirectBase + "/api/auth/steam/callback"
                : redirect;

        // Fix #6: if the frontend supplied a CSRF state token, embed it in the
        // return_to URL so Steam carries it back in the redirect, and the
        // callback can verify it against the browser's cookie.
        final String returnTo = (state != null && SAFE_STATE_PATTERN.matcher(state).matches())
                ? baseReturnTo + (baseReturnTo.contains("?") ? "&" : "?") + "state=" + enc(state)
                : baseReturnTo;

        final String realm = deriveOriginSafe(returnTo, defaultRedirectBase);
        final String url = OPENID_ENDPOINT + "?openid.ns=" + enc("http://specs.openid.net/auth/2.0")
                + "&openid.mode=checkid_setup"
                + "&openid.return_to=" + enc(returnTo)
                + "&openid.realm=" + enc(realm)
                + "&openid.identity=" + enc("http://specs.openid.net/auth/2.0/identifier_select")
                + "&openid.claimed_id=" + enc("http://specs.openid.net/auth/2.0/identifier_select");
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/steam/callback")
    public ResponseEntity<?> callback(@RequestParam Map<String, String> params) {
        try {
            final String body = buildCheckAuthBody(params);

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
        return ResponseEntity.ok(Map.of("valid", true, "steamId", steamId));
    }
}
