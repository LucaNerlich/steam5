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

    private final AuthTokenService tokenService;
    private final SteamUserService steamUserService;

    @Value("${auth.redirectBase:https://steam5.org}")
    private String defaultRedirectBase;

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
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
        for (Map.Entry<String, java.util.List<String>> e : form.entrySet()) {
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
            if (r.getPort() != -1) origin.append(":" + r.getPort());
            return origin.toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    @GetMapping("/steam/login")
    public ResponseEntity<Void> startLogin(@RequestParam(value = "redirect", required = false) String redirect,
                                           jakarta.servlet.http.HttpServletRequest request) {
        // Compute return_to and realm. Realm MUST be the origin (scheme://host[:port]) of return_to
        final String returnTo = (redirect == null || redirect.isBlank()) ? defaultRedirectBase + "/api/auth/steam/callback" : redirect;
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
            // Verify assertion with Steam via check_authentication
            final String body = buildCheckAuthBody(params);
            final String opEndpoint = params.getOrDefault("openid.op_endpoint", OPENID_ENDPOINT);
            HttpResponse<String> res;
            try (HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()) {
                final HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(opEndpoint))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .header(HttpHeaders.ACCEPT, "text/plain")
                        .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                        .header(HttpHeaders.USER_AGENT, "steam5-auth/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
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

            // Enrich user profile (persona name) best-effort
            steamUserService.updateUserProfile(steamId);

            // Issue signed token
            final String token = tokenService.generateToken(steamId);
            return ResponseEntity.ok(Map.of("steamId", steamId, "token", token));
        } catch (Exception e) {
            log.error("Steam OpenID callback error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "auth_failed"));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam("token") String token) {
        final String steamId = tokenService.verifyToken(token);
        if (steamId == null) return ResponseEntity.status(401).body(Map.of("valid", false));
        return ResponseEntity.ok(Map.of("valid", true, "steamId", steamId));
    }
}


