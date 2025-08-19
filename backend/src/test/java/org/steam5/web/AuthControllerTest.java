package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthControllerTest {

    @Test
    void startLogin_redirectsToSteamOpenId() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        AuthController controller = new AuthController(token, users);
        ResponseEntity<Void> res = controller.startLogin(null, null);
        assertEquals(302, res.getStatusCode().value());
        assertNotNull(res.getHeaders().getLocation());
        final var loc = res.getHeaders().getLocation();
        assertNotNull(loc);
        assertTrue(loc.toString().contains("steamcommunity.com/openid/login"));
    }

    @Test
    void validate_token_ok_and_invalid() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        AuthController controller = new AuthController(token, users);

        when(token.verifyToken("good")).thenReturn("123");
        when(token.verifyToken("bad")).thenReturn(null);

        var ok = controller.validate("good");
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getBody());
        final var okBody = ok.getBody();
        assertNotNull(okBody);
        assertEquals(true, ((Map<?, ?>) okBody).get("valid"));

        var bad = controller.validate("bad");
        assertEquals(401, bad.getStatusCode().value());
        assertNotNull(bad.getBody());
        final var badBody = bad.getBody();
        assertNotNull(badBody);
        assertEquals(false, ((Map<?, ?>) badBody).get("valid"));
    }
}


