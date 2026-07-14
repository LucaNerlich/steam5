package org.steam5.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseDateParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final DateTimeFormatter STEAM_DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STEAM_MONTH_DAY_YEAR =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private ReleaseDateParser() {
    }

    public static boolean isComingSoonOrUnknown(final String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            return true;
        }
        final String lower = releaseDate.toLowerCase(Locale.ROOT);
        return lower.contains("coming soon")
                || lower.contains("to be announced")
                || lower.contains("tbd");
    }

    public static Optional<Integer> parseYear(final String releaseDate) {
        if (isComingSoonOrUnknown(releaseDate)) {
            return Optional.empty();
        }
        final Matcher matcher = YEAR_PATTERN.matcher(releaseDate);
        Integer year = null;
        while (matcher.find()) {
            year = Integer.parseInt(matcher.group());
        }
        return Optional.ofNullable(year);
    }

    public static Optional<LocalDate> parseDate(final String releaseDate) {
        if (isComingSoonOrUnknown(releaseDate)) {
            return Optional.empty();
        }
        final String trimmed = releaseDate.trim();
        try {
            return Optional.of(LocalDate.parse(trimmed, STEAM_DAY_MONTH_YEAR));
        } catch (DateTimeParseException ignored) {
            // try alternate format
        }
        try {
            return Optional.of(LocalDate.parse(trimmed, STEAM_MONTH_DAY_YEAR));
        } catch (DateTimeParseException ignored) {
            // fall back to year-only
        }
        return parseYear(releaseDate).map(year -> LocalDate.of(year, 1, 1));
    }

    public static boolean isReleasedWithinDays(final String releaseDate, final int days) {
        if (days <= 0) {
            return false;
        }
        return parseDate(releaseDate)
                .map(date -> !date.isBefore(LocalDate.now().minusDays(days)))
                .orElse(false);
    }
}
