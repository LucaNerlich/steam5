package org.steam5.web;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtagsTest {

    @Test
    void terminatorMakesFieldBoundariesMatter() {
        // Two of the previous copies concatenated fields without a separator, so different
        // content could hash identically.
        assertNotEquals(Etags.weakEtag(List.of("ab", "c")), Etags.weakEtag(List.of("a", "bc")));
    }

    @Test
    void distinguishesNullFromEmptyString() {
        assertNotEquals(Etags.weakEtag(Arrays.asList("a", null)), Etags.weakEtag(List.of("a", "")));
    }

    @Test
    void pinsTheDigestForAKnownFieldList() {
        assertEquals("W/\"dcb24de526c9227b93932f68a679fe7a3061215e91698fbdb7ffa1f63cd78728\"",
                Etags.weakEtag(List.of("171", "Game")));
    }

    @Test
    void isStableForTheSameFields() {
        assertEquals(Etags.weakEtag(List.of("171", "Game", "2020-01-01")),
                Etags.weakEtag(List.of("171", "Game", "2020-01-01")));
    }

    @Test
    void formatsAsAWeakEtag() {
        final String etag = Etags.weakEtag(List.of("x"));

        assertTrue(etag != null && etag.matches("W/\"[0-9a-f]{64}\""), "unexpected ETag: " + etag);
    }
}
