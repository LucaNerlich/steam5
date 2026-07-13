package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.steam5.domain.Guess;
import org.steam5.domain.GuessStats;
import org.steam5.domain.StreakCalculator;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
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
     * Aggregates all-time stats (pre-computed in SQL, already ordered by total points
     * descending) into a leaderboard.
     */
    public List<LeaderEntry> buildAllTimeLeaderboard(final LocalDate today) {
        final List<GuessRepository.AllTimeStatsRow> rows = guessRepository.aggregateAllTimeStats();
        if (rows.isEmpty()) {
            return List.of();
        }

        final List<String> steamIds = rows.stream().map(GuessRepository.AllTimeStatsRow::getSteamId).toList();
        final Map<String, User> usersById = userRepository.findAllById(steamIds).stream()
                .collect(Collectors.toMap(User::getSteamId, user -> user));
        final Map<String, List<LocalDate>> streakDatesById = guessRepository
                .findDistinctDatesUpToForUsers(steamIds, today)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        return rows.stream()
                .map(row -> {
                    final User user = usersById.get(row.getSteamId());
                    final List<LocalDate> dates = streakDatesById.getOrDefault(row.getSteamId(), List.of());
                    final int streak = StreakCalculator.currentStreak(dates, today);
                    return getLeaderEntry(
                            row.getSteamId(),
                            row.getTotalPoints() != null ? row.getTotalPoints() : 0L,
                            row.getRounds() != null ? row.getRounds() : 0L,
                            row.getHits() != null ? row.getHits() : 0L,
                            row.getFlops() != null ? row.getFlops() : 0L,
                            row.getTooHigh() != null ? row.getTooHigh() : 0L,
                            row.getTooLow() != null ? row.getTooLow() : 0L,
                            row.getAvgPoints() != null ? row.getAvgPoints() : 0.0,
                            streak,
                            user
                    );
                })
                .toList();
    }

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

    private LeaderEntry getLeaderEntry(final String steamId, final long totalPoints, final long rounds, final long hits, final long flops, final long tooHigh, final long tooLow, final double avgPoints, final int streak, final User user) {
        final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
        final String avatar = user != null && user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : null;
        final String avatarBlurdata = user != null && user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : null;
        final String profileUrl = user != null && user.getProfileUrl() != null && !user.getProfileUrl().isBlank() ? user.getProfileUrl() : null;
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
