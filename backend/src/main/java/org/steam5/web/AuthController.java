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

import java.net.URI;
import java.net.URLEncoder;
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

    @Value("${auth.redirectBase:https://steam5.org}")
    private String defaultRedirectBase;

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String buildCheckAuthBody(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("openid.mode", "check_authentication");
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("openid.")) {
                form.add(e.getKey(), e.getValue());
            }
        }
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

    @GetMapping("/steam/login")
    public ResponseEntity<Void> startLogin(@RequestParam(value = "redirect", required = false) String redirect) {
        // Build OpenID authentication request
        String realm = defaultRedirectBase;
        String returnTo = (redirect == null || redirect.isBlank()) ? defaultRedirectBase + "/api/auth/steam/callback" : redirect;

        String url = OPENID_ENDPOINT + "?openid.ns=" + enc("http://specs.openid.net/auth/2.0")
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
            String body = buildCheckAuthBody(params);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(OPENID_ENDPOINT))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            String resBody = res.body();
            if (resBody == null || !resBody.contains("is_valid:true")) {
                log.warn("Steam OpenID verification failed: status={} body={} params={}", res.statusCode(), resBody, params.keySet());
                return ResponseEntity.status(401).body(Map.of("error", "invalid_openid"));
            }

            // Extract SteamID64 from claimed_id
            String claimed = params.get("openid.claimed_id");
            if (claimed == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_claimed_id"));
            }
            Matcher m = STEAM_ID_PATTERN.matcher(claimed);
            if (!m.find()) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_claimed_id"));
            }
            String steamId = m.group(1);

            // Issue signed token
            String token = tokenService.generateToken(steamId);

            return ResponseEntity.ok(Map.of("steamId", steamId, "token", token));
        } catch (Exception e) {
            log.error("Steam OpenID callback error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "auth_failed"));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam("token") String token) {
        String steamId = tokenService.verifyToken(token);
        if (steamId == null) return ResponseEntity.status(401).body(Map.of("valid", false));
        return ResponseEntity.ok(Map.of("valid", true, "steamId", steamId));
    }
}


