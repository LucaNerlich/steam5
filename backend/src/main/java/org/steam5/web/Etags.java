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
 * null field contributes a marker byte instead of content, so it stays distinct from an empty
 * string (both are valid JSON values with different payloads).</p>
 *
 * <p>{@link #weakEtagForGroups} closes each inner list with a group-terminator byte, for
 * responses whose content is several lists (labels and titles, one group per archive day).
 * Flattening them loses where one list ended, so {@code ["ab"], ["c"]} and {@code ["ab", "c"]}
 * hashed the same and a moved boundary kept answering 304 with the old body.</p>
 */
@Slf4j
final class Etags {

    private static final byte FIELD_TERMINATOR = 0;
    private static final byte NULL_FIELD_MARKER = 1;
    private static final byte GROUP_TERMINATOR = 2;

    private Etags() {
    }

    /**
     * @return the weak ETag (`W/"&lt;hex&gt;"`) for the fields, or null when SHA-256 is
     * unavailable (an ETag is an optimisation: the caller then serves the body instead)
     */
    static String weakEtag(List<String> fields) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFields(digest, fields);
            return formatWeakEtag(digest);
        } catch (NoSuchAlgorithmException e) {
            log.debug("ETag generation failed", e);
            return null;
        }
    }

    /**
     * @return the weak ETag for grouped fields: every inner list is terminated by a
     * group-terminator byte after its NUL-terminated fields, so the boundary between the
     * groups is part of the digest; a null inner list contributes only its terminator
     */
    static String weakEtagForGroups(List<List<String>> groups) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (final List<String> group : groups) {
                if (group != null) {
                    updateFields(digest, group);
                }
                digest.update(GROUP_TERMINATOR);
            }
            return formatWeakEtag(digest);
        } catch (NoSuchAlgorithmException e) {
            log.debug("ETag generation failed", e);
            return null;
        }
    }

    private static void updateFields(MessageDigest digest, List<String> fields) {
        for (final String field : fields) {
            if (field == null) {
                digest.update(NULL_FIELD_MARKER);
            } else {
                digest.update(field.getBytes(StandardCharsets.UTF_8));
            }
            digest.update(FIELD_TERMINATOR);
        }
    }

    private static String formatWeakEtag(MessageDigest digest) {
        return "W/\"" + HexFormat.of().formatHex(digest.digest()) + "\"";
    }
}
