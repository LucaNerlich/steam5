package org.steam5.game.year;

import java.util.List;

public final class YearGuessEvaluator {

    public static final int MAX_HINTS = 3;

    private YearGuessEvaluator() {
    }

    public static int distance(final int guessYear, final int actualYear) {
        return Math.abs(guessYear - actualYear);
    }

    public static boolean isExactMatch(final int guessYear, final int actualYear) {
        return guessYear == actualYear;
    }

    /**
     * Four scoring tiers: no hints (5), one hint (4), two hints (3), three hints (2).
     */
    public static int maxPointsForHintsUsed(final int hintsUsed, final YearGameConfig config) {
        final int cappedHints = Math.max(0, Math.min(hintsUsed, MAX_HINTS));
        return Math.max(0, config.getMaxPoints() - cappedHints);
    }

    public static int scoreExactGuess(final int hintsUsed, final YearGameConfig config) {
        return maxPointsForHintsUsed(hintsUsed, config);
    }

    /**
     * Hint level is 1-based. Hint 1 unlocks at the first threshold, hint 2 at the second, etc.
     */
    public static boolean isHintUnlocked(final int hintLevel, final int bestDistance, final YearGameConfig config) {
        if (hintLevel < 1 || hintLevel > MAX_HINTS) {
            return false;
        }
        final List<Integer> thresholds = config.getHintDistanceThresholds();
        if (thresholds == null || thresholds.size() < hintLevel) {
            return false;
        }
        return bestDistance >= thresholds.get(hintLevel - 1);
    }

    public static List<Integer> unlockableHintLevels(final int bestDistance, final int hintsUsed, final YearGameConfig config) {
        final java.util.ArrayList<Integer> levels = new java.util.ArrayList<>(MAX_HINTS);
        for (int level = 1; level <= MAX_HINTS; level++) {
            if (level <= hintsUsed) {
                continue;
            }
            if (level > hintsUsed + 1) {
                continue;
            }
            if (isHintUnlocked(level, bestDistance, config)) {
                levels.add(level);
            }
        }
        return levels;
    }
}
