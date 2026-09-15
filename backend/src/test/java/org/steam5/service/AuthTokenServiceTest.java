package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceTest {

    private static final String STEAM_ID = "76561198000000000";
    private static final String SECRET = "test-only-secret-at-least-32-bytes-long!!";

    private UserRepository userRepository;
    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        final Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        userRepository = mock(UserRepository.class);
        service = new AuthTokenService(SECRET, environment, userRepository);
    }

    @Test
    void aFreshlyGeneratedTokenVerifiesToItsSubject() {
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.empty());
        final String token = service.generateToken(STEAM_ID);
        assertEquals(STEAM_ID, service.verifyToken(token));
    }

    @Test
    void aTokenIssuedBeforeLogoutIsRejectedAfterInvalidation() {
        final String token = service.generateToken(STEAM_ID);

        // Logout: invalidate everything issued up to and including now.
        final User user = new User();
        user.setSteamId(STEAM_ID);
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));
        service.invalidateTokensIssuedBefore(STEAM_ID, OffsetDateTime.now().plusSeconds(1));

        // The same, already-issued token must no longer verify.
        assertNull(service.verifyToken(token));
    }

    @Test
    void aTokenIssuedAfterLogoutStillVerifies() {
        final User user = new User();
        user.setSteamId(STEAM_ID);
        user.setTokenNotValidBefore(OffsetDateTime.now().minusSeconds(60));
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

        final String freshToken = service.generateToken(STEAM_ID);

        assertEquals(STEAM_ID, service.verifyToken(freshToken));
    }

    @Test
    void invalidateTokensIssuedBefore_persistsTheTimestampOnTheUser() {
        final User user = new User();
        user.setSteamId(STEAM_ID);
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

        service.invalidateTokensIssuedBefore(STEAM_ID, OffsetDateTime.now());

        verify(userRepository).save(eq(user));
    }

    @Test
    void invalidatingAnUnknownUserDoesNothing() {
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.empty());
        service.invalidateTokensIssuedBefore(STEAM_ID, OffsetDateTime.now());
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }
}
