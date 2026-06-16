package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.repository.UserRepository;
import org.steam5.service.AuthTokenService;
import org.steam5.service.SteamUserService;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthControllerTest {

    @Test
    void startLogin_redirectsToSteamOpenId() {
        AuthTokenService token = mock(AuthTokenService.class);
        SteamUserService users = mock(SteamUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthController controller = new AuthController(token, users, userRepo);
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
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById("123")).thenReturn(Optional.empty());
        AuthController controller = new AuthController(token, users, userRepo);

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
        AuthController controller = new AuthController(token, users, userRepo);

        when(token.verifyToken("good")).thenReturn("123");

        // Bearer prefix is required for the success path.
        var ok = controller.validate("Bearer good");
        assertEquals(200, ok.getStatusCode().value());
        // Per-user token validation must never be stored by any cache (shared or private).
        assertEquals("no-store", ok.getHeaders().getCacheControl());
    }
}


