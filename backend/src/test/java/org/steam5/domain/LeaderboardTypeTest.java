package org.steam5.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardTypeTest {

    @Test
    void values_containsAllSixTypesInDeclaredOrder() {
        // Declared order matters: LeaderboardRefreshService derives each type's advisory-lock
        // key from ADVISORY_LOCK_BASE + type.ordinal(), so reordering this enum would silently
        // change lock keys across a deploy.
        assertArrayEquals(
                new LeaderboardType[]{
                        LeaderboardType.ALL_TIME,
                        LeaderboardType.MONTHLY,
                        LeaderboardType.WEEKLY,
                        LeaderboardType.SEASON,
                        LeaderboardType.HARDEST_GAMES,
                        LeaderboardType.PERFECT_DAYS
                },
                LeaderboardType.values()
        );
    }

    @Test
    void valueOf_perfectDays_returnsThePerfectDaysConstant() {
        assertEquals(LeaderboardType.PERFECT_DAYS, LeaderboardType.valueOf("PERFECT_DAYS"));
    }

    @Test
    void perfectDays_hasOrdinalFive() {
        assertEquals(5, LeaderboardType.PERFECT_DAYS.ordinal());
    }

    @Test
    void valueOf_unknownLiteral_throws() {
        assertThrows(IllegalArgumentException.class, () -> LeaderboardType.valueOf("NOT_A_REAL_TYPE"));
    }
}