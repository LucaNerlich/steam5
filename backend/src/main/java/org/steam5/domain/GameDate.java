package org.steam5.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Single owner of the game's calendar anchor. Using UTC consistently ensures
 * "today" resolves to the same date for all players and aligns with the
 * daily-pick scheduling that also runs in UTC.
 */
public final class GameDate {

    private GameDate() {}

    /** Returns today's date in UTC. */
    public static LocalDate todayUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }
}
