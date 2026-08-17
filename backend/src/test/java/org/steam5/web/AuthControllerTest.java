package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.auth.OpenIdNonceStore;
import org.steam5.repository.UserRepository;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthControllerTest {

    private static final String CALLBACK_BASE = "https://steam5.org/api/auth/steam/callback";
    private static final String STEAM_ID = "76561198000000000";
    private static final String CLAIMED_ID = "https://steamcommunity.com/openid/id/" + STEAM_ID;

    private AuthController controller(AuthTokenService token, SteamUserService users,
                                      UserRepository userRepo, OpenIdNonceStore nonceStore) {
        return new AuthController(token, users, userRepo, nonceStore);
    }

    /** Controller whose Steam verification step is stubbed to succeed. */
    private AuthController verifiedController(OpenIdNonceStore nonceStore) {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(token.generateToken(anyString())).thenReturn("fresh-token");
        return new AuthController(token, users, userRepo, nonceStore) {
            @Override
            boolean steamVerifies(Map<String, String> params) {
                return true;
            }
        };
    }

    /** Builds an assertion param map for a callback issued against {@code returnToBase}. */
    private Map<String, String> assertionParams(OpenIdNonceStore nonceStore, String returnToBase) {
        final String nonce = nonceStore.issue(returnToBase);
        final Map<String, String> params = new HashMap<>();
        params.put("openid.mode", "id_res");
        params.put("openid.claimed_id", CLAIMED_ID);
        params.put("openid.identity", CLAIMED_ID);
        params.put("openid.signed", "assoc_handle,signed,claimed_id,identity,return_to");
        params.put("openid.return_to", returnToBase + "?state=abc&nonce=" + nonce);
        return params;
    }

    @Test
    void startLogin_redirectsToSteamOpenId() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthController controller = controller(token, users, userRepo, new OpenIdNonceStore());
        ResponseEntity<Void> res = controller.startLogin(null, null);
        assertEquals(302, res.getStatusCode().value());
        assertNotNull(res.getHeaders().getLocation());
        final var loc = res.getHeaders().getLocation();
        assertNotNull(loc);
        assertTrue(loc.toString().contains("steamcommunity.com/openid/login"));
    }

    @Test
    void startLogin_embedsSingleUseNonceInReturnTo() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthController controller = controller(token, users, userRepo, new OpenIdNonceStore());
        ResponseEntity<Void> res = controller.startLogin(null, null);
        final String location = res.getHeaders().getLocation().toString();
        assertNotNull(location);
        final String returnTo = URLDecoder.decode(location.substring(location.indexOf("openid.return_to=") + "openid.return_to=".length(), location.indexOf("&openid.realm=")), StandardCharsets.UTF_8);
        assertTrue(returnTo.contains("nonce="));
        assertTrue(returnTo.substring(returnTo.indexOf("nonce=") + 6).length() >= 32);
    }

    @Test
    void validate_token_ok_and_invalid() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById("123")).thenReturn(Optional.empty());
        AuthController controller = controller(token, users, userRepo, new OpenIdNonceStore());

        when(token.verifyToken("good")).thenReturn("123");
        when(token.verifyToken("bad")).thenReturn(null);

        // validate() reads an Authorization header, which must carry the "Bearer " scheme.
        var ok = controller.validate("Bearer good");
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getBody());
        final var okBody = ok.getBody();
        assertNotNull(okBody);
        assertEquals(true, ((Map<?, ?>) okBody).get("valid"));

        var bad = controller.validate("Bearer bad");
        assertEquals(401, bad.getStatusCode().value());
        assertNotNull(bad.getBody());
        final var badBody = bad.getBody();
        assertNotNull(badBody);
        assertEquals(false, ((Map<?, ?>) badBody).get("valid"));
    }

    @Test
    void validate_okResponseIsNeverCached() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById("123")).thenReturn(Optional.empty());
        AuthController controller = controller(token, users, userRepo, new OpenIdNonceStore());

        when(token.verifyToken("good")).thenReturn("123");

        // Bearer prefix is required for the success path.
        var ok = controller.validate("Bearer good");
        assertEquals(200, ok.getStatusCode().value());
        // Per-user token validation must never be stored by any cache (shared or private).
        assertEquals("no-store", ok.getHeaders().getCacheControl());
    }

    @Test
    void callback_validAssertion_returnsTokenWithNoStore() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);

        final var res = controller.callback(assertionParams(nonceStore, CALLBACK_BASE));

        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals(STEAM_ID, ((Map<?, ?>) res.getBody()).get("steamId"));
        assertEquals("fresh-token", ((Map<?, ?>) res.getBody()).get("token"));
        assertEquals("no-store", res.getHeaders().getCacheControl());
    }

    @Test
    void callback_replayedAssertion_isRejected() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);
        final Map<String, String> params = assertionParams(nonceStore, CALLBACK_BASE);

        assertEquals(200, controller.callback(params).getStatusCode().value());
        // Same nonce again → 401 (single-use).
        assertEquals(401, controller.callback(params).getStatusCode().value());
    }

    @Test
    void callback_missingNonce_isRejected() {
        final AuthController controller = verifiedController(new OpenIdNonceStore());
        final Map<String, String> params = new HashMap<>();
        params.put("openid.claimed_id", CLAIMED_ID);
        params.put("openid.identity", CLAIMED_ID);
        params.put("openid.signed", "claimed_id");
        params.put("openid.return_to", CALLBACK_BASE);

        assertEquals(401, controller.callback(params).getStatusCode().value());
    }

    @Test
    void callback_returnToMismatch_isRejected() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);
        // Nonce issued for the real callback base, but the assertion echoes a
        // different return_to (attacker-controlled realm/redirect).
        final String nonce = nonceStore.issue(CALLBACK_BASE);
        final Map<String, String> params = new HashMap<>();
        params.put("openid.claimed_id", CLAIMED_ID);
        params.put("openid.identity", CLAIMED_ID);
        params.put("openid.signed", "claimed_id");
        params.put("openid.return_to", "https://evil.example.com/api/auth/steam/callback?nonce=" + nonce);

        assertEquals(401, controller.callback(params).getStatusCode().value());
    }

    @Test
    void callback_identityMismatch_isRejected() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);
        final Map<String, String> params = assertionParams(nonceStore, CALLBACK_BASE);
        params.put("openid.identity", "https://steamcommunity.com/openid/id/11111111111111111");

        assertEquals(400, controller.callback(params).getStatusCode().value());
    }

    @Test
    void callback_unsignedClaimedId_isRejected() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);
        final Map<String, String> params = assertionParams(nonceStore, CALLBACK_BASE);
        params.put("openid.signed", "identity");

        assertEquals(400, controller.callback(params).getStatusCode().value());
    }

    @Test
    void callback_claimedIdWithTrailingGarbage_isRejected() {
        final OpenIdNonceStore nonceStore = new OpenIdNonceStore();
        final AuthController controller = verifiedController(nonceStore);
        final Map<String, String> params = assertionParams(nonceStore, CALLBACK_BASE);
        // Unanchored matching would accept this; matches() must not.
        params.put("openid.claimed_id", CLAIMED_ID + "/../evil");
        params.put("openid.identity", CLAIMED_ID + "/../evil");

        assertEquals(400, controller.callback(params).getStatusCode().value());
    }

    @Test
    void baseOf_stripsQueryButKeepsPath() {
        assertEquals(CALLBACK_BASE, AuthController.baseOf(CALLBACK_BASE + "?state=abc&nonce=xyz"));
        assertNull(AuthController.baseOf("not a url"));
        assertNull(AuthController.baseOf(null));
    }

    @Test
    void queryParam_extractsValues() {
        assertEquals("xyz", AuthController.queryParam(CALLBACK_BASE + "?nonce=xyz", "nonce"));
        assertEquals("xyz", AuthController.queryParam(CALLBACK_BASE + "?state=abc&nonce=xyz", "nonce"));
        assertNull(AuthController.queryParam(CALLBACK_BASE, "nonce"));
        assertNull(AuthController.queryParam(null, "nonce"));
    }
}
