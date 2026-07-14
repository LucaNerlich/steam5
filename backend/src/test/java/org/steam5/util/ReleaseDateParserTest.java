package org.steam5.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseDateParserTest {

    @Test
    void parseYear_fromSteamDayMonthYear() {
        assertEquals(Optional.of(2025), ReleaseDateParser.parseYear("1 Aug, 2025"));
        assertEquals(Optional.of(2024), ReleaseDateParser.parseYear("19 Nov, 2024"));
    }

    @Test
    void parseYear_fromYearOnly() {
        assertEquals(Optional.of(2012), ReleaseDateParser.parseYear("2012"));
    }

    @Test
    void parseYear_rejectsComingSoon() {
        assertTrue(ReleaseDateParser.parseYear("Coming soon").isEmpty());
        assertTrue(ReleaseDateParser.parseYear(null).isEmpty());
    }

    @Test
    void parseDate_parsesFullSteamDate() {
        assertEquals(Optional.of(LocalDate.of(2024, 11, 19)), ReleaseDateParser.parseDate("19 Nov, 2024"));
    }

    @Test
    void isReleasedWithinDays_detectsRecentRelease() {
        final String recent = LocalDate.now().minusDays(2).format(
                java.time.format.DateTimeFormatter.ofPattern("d MMM, yyyy", java.util.Locale.ENGLISH)
        );
        assertTrue(ReleaseDateParser.isReleasedWithinDays(recent, 7));
        assertFalse(ReleaseDateParser.isReleasedWithinDays("19 Nov, 2020", 7));
    }
}
