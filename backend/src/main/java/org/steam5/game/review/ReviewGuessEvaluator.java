package org.steam5.game.review;

import org.steam5.config.ReviewGameConfig;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewGuessEvaluator {

    private ReviewGuessEvaluator() {
    }

    public static int scorePoints(final List<String> buckets, final String selected, final String actual) {
        final int si = buckets.indexOf(selected);
        final int ai = buckets.indexOf(actual);
        if (si < 0 || ai < 0) {
            return 0;
        }
        final int d = Math.abs(si - ai);
        final int max = 5;
        final int step = 2;
        return Math.max(0, max - step * d);
    }

    public static String inferBucket(final int totalReviews, final ReviewGameConfig config) {
        final List<Integer> bounds = config.getBucketBoundaries();
        if (bounds == null || bounds.isEmpty()) {
            return totalReviews + "+";
        }

        int prev = ReviewPickGenerator.MIN_BUCKET_BOUND;
        for (int b : bounds) {
            if (totalReviews <= b) {
                return (prev == ReviewPickGenerator.MIN_BUCKET_BOUND ? "1-" + b : (prev + ReviewPickGenerator.MIN_BUCKET_BOUND) + "-" + b);
            }
            prev = b;
        }
        return bounds.getLast() + "+";
    }

    public static boolean isCorrectForLabel(final String label, final long totalReviews) {
        final Range range = parseBucketLabel(label);
        if (range.upper == null) {
            return totalReviews >= range.lower;
        }
        return totalReviews >= range.lower && totalReviews <= range.upper;
    }

    private static Range parseBucketLabel(final String label) {
        if (label == null) {
            throw new IllegalArgumentException("label null");
        }
        String s = label.trim();
        s = s.replaceAll("[\\s,._]", "");
        if (s.contains("+") || s.contains("≥") || s.contains(">=")) {
            final String digits = s.replaceAll("[^0-9]", "");
            final long lower = digits.isEmpty() ? 0L : Long.parseLong(digits);
            return new Range(lower, null);
        }
        final Matcher matcher = Pattern.compile("(?i)(\\d+)[-–—](\\d+)").matcher(s);
        if (matcher.find()) {
            final long a = Long.parseLong(matcher.group(1));
            final long b = Long.parseLong(matcher.group(2));
            final long lower = Math.min(a, b);
            final long upper = Math.max(a, b);
            return new Range(lower, upper);
        }
        final Matcher one = Pattern.compile("(\\d+)").matcher(s);
        if (one.find()) {
            final long v = Long.parseLong(one.group(1));
            return new Range(v, v);
        }
        return new Range(0L, null);
    }

    private record Range(long lower, Long upper) {
    }
}
