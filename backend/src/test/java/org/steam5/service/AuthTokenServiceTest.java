package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        // Relative to now: the JWT expiry is 30 days after issue, so a hard-coded
        // issue time eventually expires and no longer exercises the cutoff.
        final Instant issuedAt = Instant.now().minusSeconds(60);
        final String token = service.generateToken(STEAM_ID, issuedAt);

        final User user = new User();
        user.setSteamId(STEAM_ID);
        user.setTokenNotValidBefore(OffsetDateTime.ofInstant(Instant.now().minusSeconds(30), ZoneOffset.UTC));
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

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
    void logoutThenLoginWithinTheSameSecondKeepsTheNewTokenValid() {
        // Relative to now: the JWT expiry is 30 days after issue, so a hard-coded
        // issue time eventually expires and no longer exercises the cutoff.
        final OffsetDateTime logoutAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(100_000_000);
        when(userRepository.advanceTokenNotValidBefore(STEAM_ID, logoutAt.withOffsetSameInstant(ZoneOffset.UTC).withNano(0)))
                .thenReturn(1); // known user: the conditional update records the cutoff
        service.invalidateTokensIssuedBefore(STEAM_ID, logoutAt);

        final OffsetDateTime storedCutoff = logoutAt.withOffsetSameInstant(ZoneOffset.UTC).withNano(0);
        verify(userRepository).advanceTokenNotValidBefore(STEAM_ID, storedCutoff);

        final User user = new User();
        user.setSteamId(STEAM_ID);
        user.setTokenNotValidBefore(storedCutoff);
        when(userRepository.findById(STEAM_ID)).thenReturn(Optional.of(user));

        final String token = service.generateToken(STEAM_ID, storedCutoff.toInstant().plusMillis(900));

        assertEquals(STEAM_ID, service.verifyToken(token));
    }

    @Test
    void invalidateTokensIssuedBefore_usesAnAtomicWholeSecondUpdate() {
        final OffsetDateTime requested = OffsetDateTime.parse("2026-09-15T14:00:00.987654321+02:00");
        when(userRepository.advanceTokenNotValidBefore(STEAM_ID, OffsetDateTime.parse("2026-09-15T12:00:00Z")))
                .thenReturn(1);

        service.invalidateTokensIssuedBefore(STEAM_ID, requested);

        verify(userRepository).advanceTokenNotValidBefore(
                eq(STEAM_ID), eq(OffsetDateTime.parse("2026-09-15T12:00:00Z")));
        verify(userRepository, never()).findById(STEAM_ID);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void invalidatingAUserWithNoRowStillRecordsTheCutoff() {
        // The defect: 0 rows affected was treated as success. `verifyToken` skips the cutoff
        // when the row is missing, so the token stayed valid for its full 30 days.
        final OffsetDateTime instant = OffsetDateTime.parse("2026-09-15T12:00:00Z");
        when(userRepository.advanceTokenNotValidBefore(STEAM_ID, instant)).thenReturn(0);

        service.invalidateTokensIssuedBefore(STEAM_ID, instant);

        final ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(STEAM_ID, captor.getValue().getSteamId());
        assertEquals(instant, captor.getValue().getTokenNotValidBefore());
    }
}
