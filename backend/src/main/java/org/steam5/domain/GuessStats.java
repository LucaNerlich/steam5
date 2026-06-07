package org.steam5.domain;

import java.util.List;

/**
 * Aggregated statistics over a list of guesses. Both the leaderboard and profile
 * controllers previously computed these six fields with identical stream operations;
 * this record concentrates the logic in one place.
 */
public record GuessStats(
        long totalPoints,
        long rounds,
        long hits,
        long flops,
        long tooHigh,
        long tooLow,
        double avgPoints
) {
    public static GuessStats from(final List<Guess> guesses) {
        final long totalPoints = guesses.stream().mapToLong(Guess::getPoints).sum();
        final long rounds = guesses.size();
        final long hits = guesses.stream()
                .filter(g -> g.getSelectedBucket().equals(g.getActualBucket()))
                .count();
        final long flops = guesses.stream().filter(g -> g.getPoints() == 0).count();
        final long tooHigh = guesses.stream()
                .filter(g -> BucketLabel.order(g.getSelectedBucket()) > BucketLabel.order(g.getActualBucket()))
                .count();
        final long tooLow = guesses.stream()
                .filter(g -> BucketLabel.order(g.getSelectedBucket()) < BucketLabel.order(g.getActualBucket()))
                .count();
        final double avgPoints = rounds > 0 ? (double) totalPoints / rounds : 0.0;
        return new GuessStats(totalPoints, rounds, hits, flops, tooHigh, tooLow, avgPoints);
    }
}
