package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.steam5.domain.Guess;
import org.steam5.domain.GuessStats;
import org.steam5.domain.StreakCalculator;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final LeaderboardMvRepository leaderboardMvRepository;

    /**
     * Aggregates a set of guesses (already fetched for a specific day/range) into a
     * leaderboard: one entry per player, sorted by total points descending.
     */
    public List<LeaderEntry> buildLeaderboard(final List<Guess> guesses, final LocalDate asOfDate) {
        final Map<String, List<Guess>> byUser = guesses.stream().collect(Collectors.groupingBy(Guess::getSteamId));
        final Set<String> steamIds = byUser.keySet();
        if (steamIds.isEmpty()) {
            return List.of();
        }

        final Map<String, User> usersById = userRepository.findAllById(steamIds).stream()
                .collect(Collectors.toMap(User::getSteamId, user -> user));

        final Map<String, List<LocalDate>> streakDatesById = guessRepository.findDistinctDatesUpToForUsers(List.copyOf(steamIds), asOfDate)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        return byUser.entrySet().stream()
                .map(entry -> buildEntry(entry, usersById, streakDatesById, asOfDate))
                .sorted((a, b) -> Long.compare(b.totalPoints(), a.totalPoints()))
                .toList();
    }

    /**
     * Builds the all-time leaderboard from the {@code mv_leaderboard_all_time} materialized
     * view (see backend/src/main/resources/db/mv-leaderboard-all-time.sql), overlaying each
     * player's current streak from the live guesses table.
     *
     * @param today the date used to calculate current streaks
     * @return leaderboard entries in the order provided by the materialized view (total points descending)
     */
    public List<LeaderEntry> buildAllTimeLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findAllTime(), today);
    }

    /**
     * Builds the rolling 30-day leaderboard from {@code mv_leaderboard_monthly}.
     * See backend/src/main/resources/db/mv-leaderboard-monthly.sql for the window definition.
     *
     * @param today the date used to calculate current streaks
     */
    public List<LeaderEntry> buildMonthlyLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findMonthly(), today);
    }

    /**
     * Builds the rolling 7-day ("floating") leaderboard from {@code mv_leaderboard_weekly}.
     * The non-floating (previous full Monday-Sunday week) variant is not backed by this view
     * and continues to be computed live in {@code LeaderboardController#weekly}.
     * See backend/src/main/resources/db/mv-leaderboard-weekly.sql for the window definition.
     *
     * @param today the date used to calculate current streaks
     */
    public List<LeaderEntry> buildWeeklyLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findWeekly(), today);
    }

    /**
     * Builds the current-season leaderboard from {@code mv_leaderboard_season}, which scopes
     * itself to whichever season row currently contains the database's CURRENT_DATE.
     * See backend/src/main/resources/db/mv-leaderboard-season.sql.
     *
     * @param asOfDate the date used to calculate current streaks (the earlier of "today" and
     *                 the season's end date, matching the season endpoint's existing behavior)
     */
    public List<LeaderEntry> buildSeasonLeaderboard(final LocalDate asOfDate) {
        return buildFromMv(leaderboardMvRepository.findSeason(), asOfDate);
    }

    /**
     * Shared assembly step for the four materialized-view-backed leaderboards: overlays each
     * row's current streak (computed live from {@code findDistinctDatesUpToForUsers}) onto the
     * pre-aggregated MV columns. Row order (total points descending) comes from the MV query.
     */
    private List<LeaderEntry> buildFromMv(final List<LeaderboardMvRepository.LeaderboardMvRow> rows, final LocalDate asOfDate) {
        if (rows.isEmpty()) {
            return List.of();
        }

        final List<String> steamIds = rows.stream().map(LeaderboardMvRepository.LeaderboardMvRow::getSteamId).toList();
        final Map<String, List<LocalDate>> streakDatesById = guessRepository
                .findDistinctDatesUpToForUsers(steamIds, asOfDate)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        return rows.stream()
                .map(row -> {
                    final List<LocalDate> dates = streakDatesById.getOrDefault(row.getSteamId(), List.of());
                    final int streak = StreakCalculator.currentStreak(dates, asOfDate);
                    final String personaName = row.getPersonaName() != null && !row.getPersonaName().isBlank()
                            ? row.getPersonaName() : row.getSteamId();
                    return new LeaderEntry(
                            row.getSteamId(),
                            personaName,
                            row.getTotalPoints() != null ? row.getTotalPoints() : 0L,
                            row.getRounds() != null ? row.getRounds() : 0L,
                            row.getHits() != null ? row.getHits() : 0L,
                            row.getFlops() != null ? row.getFlops() : 0L,
                            row.getTooHigh() != null ? row.getTooHigh() : 0L,
                            row.getTooLow() != null ? row.getTooLow() : 0L,
                            row.getAvgPoints() != null ? row.getAvgPoints() : 0.0,
                            streak,
                            blankToNull(row.getAvatarFull()),
                            blankToNull(row.getBlurdataAvatarFull()),
                            blankToNull(row.getProfileUrl())
                    );
                })
                .toList();
    }

    private static String blankToNull(final String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    /**
     * Builds a leaderboard entry from a user's guesses and activity dates.
     *
     * @param entry          the user's Steam ID and guesses
     * @param usersById      user profiles keyed by Steam ID
     * @param streakDatesById game dates keyed by Steam ID
     * @param asOfDate       date used to calculate the current streak
     * @return the computed leaderboard entry
     */
    private LeaderEntry buildEntry(final Map.Entry<String, List<Guess>> entry,
                                    final Map<String, User> usersById,
                                    final Map<String, List<LocalDate>> streakDatesById,
                                    final LocalDate asOfDate) {
        final String steamId = entry.getKey();
        final GuessStats stats = GuessStats.from(entry.getValue());
        final User user = usersById.get(steamId);
        final List<LocalDate> dates = streakDatesById.getOrDefault(steamId, List.of());
        final int streak = StreakCalculator.currentStreak(dates, asOfDate);
        return getLeaderEntry(steamId, stats.totalPoints(), stats.rounds(), stats.hits(),
                stats.flops(), stats.tooHigh(), stats.tooLow(), stats.avgPoints(), streak, user);
    }

    /**
     * Builds a leaderboard entry with resolved user profile details and statistics.
     *
     * @param steamId the user's Steam ID, used as the persona name when no name is available
     * @param user the user's profile data, if available
     * @return a leaderboard entry containing the supplied statistics and profile details
     */
    private LeaderEntry getLeaderEntry(final String steamId, final long totalPoints, final long rounds, final long hits, final long flops, final long tooHigh, final long tooLow, final double avgPoints, final int streak, final User user) {
        final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
        final String avatar = user != null ? blankToNull(user.getAvatarFull()) : null;
        final String avatarBlurdata = user != null ? blankToNull(user.getBlurdataAvatarFull()) : null;
        final String profileUrl = user != null ? blankToNull(user.getProfileUrl()) : null;
        return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, flops, tooHigh, tooLow, avgPoints, streak, avatar, avatarBlurdata, profileUrl);
    }

    public record LeaderEntry(String steamId,
                              String personaName,
                              long totalPoints, long rounds,
                              long hits, long flops, long tooHigh, long tooLow,
                              double avgPoints,
                              int streak,
                              String avatar,
                              String avatarBlurdata,
                              String profileUrl) {
    }
}
