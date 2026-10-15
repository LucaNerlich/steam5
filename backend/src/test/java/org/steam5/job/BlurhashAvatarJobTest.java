package org.steam5.job;

import org.junit.jupiter.api.Test;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.BlurhashService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BlurhashService#readAndEncode} returns {@code null} on every failure path
 * (non-public URL, non-2xx, timeout, undecodable image), so the avatar job must
 * treat a missing result as a skip, not as a value.
 */
class BlurhashAvatarJobTest {

    private final BlurhashService service = mock(BlurhashService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BlurhashAvatarJob job = new BlurhashAvatarJob(service, userRepository);

    @Test
    void encodeForSteamId_skipsAFailedEncodeInsteadOfThrowing() {
        final User user = userWithAvatars();
        when(userRepository.findById("steam-1")).thenReturn(Optional.of(user));
        when(service.readAndEncode("https://cdn/thumb.jpg", BlurhashService.Type.THUMBNAIL)).thenReturn(null);
        when(service.readAndEncode("https://cdn/full.jpg", BlurhashService.Type.FULL))
                .thenReturn(new BlurhashService.Encoded("FULL-HASH", "data:image/png;base64,AA"));

        final int encoded = job.encodeForSteamId("steam-1");

        assertEquals(1, encoded, "only the successful encode is counted");
        assertEquals("FULL-HASH", user.getBlurhashAvatarFull());
        assertNull(user.getBlurhashAvatar(), "a failed thumbnail encode must not be written");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void encodeForSteamId_writesBothFieldsWhenBothEncodesSucceed() {
        final User user = userWithAvatars();
        when(userRepository.findById("steam-1")).thenReturn(Optional.of(user));
        when(service.readAndEncode("https://cdn/thumb.jpg", BlurhashService.Type.THUMBNAIL))
                .thenReturn(new BlurhashService.Encoded("THUMB-HASH", null));
        when(service.readAndEncode("https://cdn/full.jpg", BlurhashService.Type.FULL))
                .thenReturn(new BlurhashService.Encoded("FULL-HASH", null));

        final int encoded = job.encodeForSteamId("steam-1");

        assertEquals(2, encoded);
        assertEquals("THUMB-HASH", user.getBlurhashAvatar());
        assertEquals("FULL-HASH", user.getBlurhashAvatarFull());
        verify(userRepository, times(2)).save(user);
    }

    private static User userWithAvatars() {
        final User user = new User();
        user.setSteamId("steam-1");
        user.setAvatar("https://cdn/thumb.jpg");
        user.setAvatarFull("https://cdn/full.jpg");
        return user;
    }
}
