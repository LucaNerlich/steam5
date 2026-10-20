package org.steam5.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonControllerCacheKeyTest {

    @Test
    void collapsesNumericSpellingsToTheSameCacheKey() {
        assertEquals("1", SeasonController.canonicalSeasonKey("1"));
        assertEquals("1", SeasonController.canonicalSeasonKey("01"));
        assertEquals("1", SeasonController.canonicalSeasonKey("+1"));
    }

    @Test
    void leavesNonNumericIdentifiersAlone() {
        assertEquals("season-one", SeasonController.canonicalSeasonKey(" season-one "));
    }
}
