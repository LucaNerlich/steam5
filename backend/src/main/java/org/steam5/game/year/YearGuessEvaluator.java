package org.steam5.game.year;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YearGuessEvaluator {

    public static final int MIN_YEAR_BOUND = 1;

    private YearGuessEvaluator() {
    }

    public static int scorePoints(final List<String> buckets, final String selected, final String actual) {
        final int selectedIndex = buckets.indexOf(selected);
        final int actualIndex = buckets.indexOf(actual);
        if (selectedIndex < 0 || actualIndex < 0) {
            return 0;
        }
        final int distance = Math.abs(selectedIndex - actualIndex);
        final int max = 5;
        final int step = 2;
        return Math.max(0, max - step * distance);
    }

    public static String inferBucket(final int releaseYear, final YearGameConfig config) {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) {
            return releaseYear + "+";
        }

        int previous = MIN_YEAR_BOUND;
        for (int bound : bounds) {
            if (releaseYear <= bound) {
                return previous == MIN_YEAR_BOUND
                        ? MIN_YEAR_BOUND + "-" + bound
                        : (previous + 1) + "-" + bound;
            }
            previous = bound;
        }
        return bounds.getLast() + "+";
    }

    public static boolean isCorrectForLabel(final String label, final int releaseYear) {
        final Range range = parseBucketLabel(label);
        if (range.upper == null) {
            return releaseYear >= range.lower;
        }
        return releaseYear >= range.lower && releaseYear <= range.upper;
    }

    private static Range parseBucketLabel(final String label) {
        if (label == null) {
            throw new IllegalArgumentException("label null");
        }
        String normalized = label.trim();
        normalized = normalized.replaceAll("[\\s,._]", "");
        if (normalized.contains("+") || normalized.contains("≥") || normalized.contains(">=")) {
            final String digits = normalized.replaceAll("[^0-9]", "");
            final long lower = digits.isEmpty() ? 0L : Long.parseLong(digits);
            return new Range(lower, null);
        }
        final Matcher matcher = Pattern.compile("(?i)(\\d+)[-–—](\\d+)").matcher(normalized);
        if (matcher.find()) {
            final long first = Long.parseLong(matcher.group(1));
            final long second = Long.parseLong(matcher.group(2));
            final long lower = Math.min(first, second);
            final long upper = Math.max(first, second);
            return new Range(lower, upper);
        }
        final Matcher single = Pattern.compile("(\\d+)").matcher(normalized);
        if (single.find()) {
            final long value = Long.parseLong(single.group(1));
            return new Range(value, value);
        }
        return new Range(0L, null);
    }

    private record Range(long lower, Long upper) {
    }
}
