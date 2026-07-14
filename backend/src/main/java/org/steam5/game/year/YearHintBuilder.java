package org.steam5.game.year;

public final class YearHintBuilder {

    private YearHintBuilder() {
    }

    public static String buildEraHint(final int actualYear) {
        final int decade = (actualYear / 10) * 10;
        return "Released in the " + decade + "s";
    }

    public static String buildNarrowRangeHint(final int actualYear, final int windowYears) {
        final int half = Math.max(1, windowYears);
        final int lower = actualYear - half;
        final int upper = actualYear + half;
        return "Released between " + lower + " and " + upper;
    }

    public static String buildStoreDateHint(final String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            return "Release date unavailable";
        }
        return releaseDate.trim();
    }
}
