package org.steam5.web;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Weak ETag helper for the JSON read endpoints.
 *
 * <p>One implementation for every endpoint that needs a content validator: SHA-256 over the
 * caller's fields, in the caller's order, each field terminated by a NUL byte. The terminator
 * is what keeps {@code ["ab", "c"]} and {@code ["a", "bc"]} apart — concatenating fields
 * without one (as two of the previous copies did) makes different content hash the same. A
 * null field contributes only its terminator, so it is distinct from an empty string.</p>
 */
@Slf4j
final class Etags {

    private static final byte FIELD_TERMINATOR = 0;

    private Etags() {
    }

    /**
     * @return the weak ETag (`W/"&lt;hex&gt;"`) for the fields, or null when SHA-256 is
     * unavailable (an ETag is an optimisation: the caller then serves the body instead)
     */
    static String weakEtag(List<String> fields) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (final String field : fields) {
                if (field != null) {
                    digest.update(field.getBytes(StandardCharsets.UTF_8));
                }
                digest.update(FIELD_TERMINATOR);
            }
            return "W/\"" + HexFormat.of().formatHex(digest.digest()) + "\"";
        } catch (NoSuchAlgorithmException e) {
            log.debug("ETag generation failed", e);
            return null;
        }
    }
}
