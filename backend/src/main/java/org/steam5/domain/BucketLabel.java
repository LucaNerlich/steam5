package org.steam5.domain;

/**
 * Single owner of the bucket-label parsing rule. A label like "100-1000" or "10000+"
 * encodes a review-count range; this class extracts the lower bound for ordering and
 * comparison. Both controllers previously carried identical private copies of this logic.
 */
public final class BucketLabel {

    private BucketLabel() {}

    /**
     * Returns the lower bound encoded in a bucket label, used to determine whether a
     * guess was too high or too low relative to the actual bucket.
     * Returns {@link Integer#MIN_VALUE} for null or unparseable labels.
     */
    public static int order(final String label) {
        if (label == null) return Integer.MIN_VALUE;
        final String s = label.trim();
        try {
            if (s.endsWith("+")) {
                return Integer.parseInt(s.substring(0, s.length() - 1));
            }
            final int dash = s.indexOf('-');
            if (dash > 0) {
                return Integer.parseInt(s.substring(0, dash));
            }
        } catch (final Exception ignored) {
        }
        return Integer.MIN_VALUE;
    }
}
