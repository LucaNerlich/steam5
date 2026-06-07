package org.steam5.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Two streak computations over a player's participation dates. Previously one lived
 * in the web layer (LeaderboardController) and the other in the service layer
 * (SeasonService), with no shared owner.
 */
public final class StreakCalculator {

    private StreakCalculator() {}

    /**
     * Current active streak of consecutive days ending at or one day before
     * {@code asOf}. The one-day grace means a player who played yesterday but not
     * yet today still has an active streak.
     *
     * @param datesDesc participation dates in <strong>descending</strong> order
     *                  (most recent first), as returned by
     *                  {@code GuessRepository.findDistinctDatesUpToForUsers}.
     */
    public static int currentStreak(final List<LocalDate> datesDesc, final LocalDate asOf) {
        if (datesDesc.isEmpty()) return 0;
        final LocalDate latest = datesDesc.get(0);
        if (!latest.equals(asOf) && !latest.equals(asOf.minusDays(1))) {
            return 0; // gap of two or more days — streak is broken
        }
        int streak = 0;
        LocalDate next = latest;
        for (final LocalDate d : datesDesc) {
            if (d.equals(next)) {
                streak++;
                next = next.minusDays(1);
            } else if (d.isBefore(next)) {
                break; // first gap
            }
            // d.isAfter(next) — future date, skip
        }
        return streak;
    }

    /**
     * Longest contiguous streak of consecutive days ever recorded.
     *
     * @param datesAsc participation dates in <strong>ascending</strong> order;
     *                 duplicates are ignored.
     */
    public static long longestStreak(final List<LocalDate> datesAsc) {
        long best = 0;
        long current = 0;
        LocalDate previous = null;
        for (final LocalDate date : datesAsc) {
            if (previous == null) {
                current = 1;
            } else if (date.equals(previous.plusDays(1))) {
                current++;
            } else if (date.equals(previous)) {
                continue; // duplicate — skip
            } else {
                current = 1;
            }
            previous = date;
            if (current > best) best = current;
        }
        return best;
    }
}
