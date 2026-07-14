package org.steam5.game.year;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YearHintBuilderTest {

    @Test
    void buildEraHint_usesDecade() {
        assertEquals("Released in the 2010s", YearHintBuilder.buildEraHint(2018));
    }

    @Test
    void buildNarrowRangeHint_centersOnActualYear() {
        assertTrue(YearHintBuilder.buildNarrowRangeHint(2020, 2).contains("2018"));
        assertTrue(YearHintBuilder.buildNarrowRangeHint(2020, 2).contains("2022"));
    }

    @Test
    void buildStoreDateHint_returnsRawSteamString() {
        assertEquals("19 Nov, 2024", YearHintBuilder.buildStoreDateHint("19 Nov, 2024"));
    }
}
