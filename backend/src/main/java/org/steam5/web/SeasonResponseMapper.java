package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.SeasonService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transforms SeasonService domain objects into HTTP response view types.
 * Keeps SeasonController a thin routing layer — route, delegate to service,
 * map result, return.
 *
 * <p>Owns all user-display-name lookups for season responses: the two
 * {@code UserRepository} bulk-fetches in {@link #mapAwards} and
 * {@link #mapTopPlayers} are the only place that queries users on behalf
 * of season endpoints.</p>
 */
@Component
@RequiredArgsConstructor
class SeasonResponseMapper {

    private final UserRepository userRepository;

    SeasonController.SeasonView mapSeason(final Season season, final List<SeasonAwardResult> awards) {
        return new SeasonController.SeasonView(
                season.getId(),
                season.getSeasonNumber(),
                season.getStartDate(),
                season.getEndDate(),
                season.getStatus(),
                season.getAwardsFinalizedAt(),
                mapAwards(awards)
        );
    }

    List<SeasonController.AwardView> mapAwards(final List<SeasonAwardResult> awards) {
        if (awards == null || awards.isEmpty()) return List.of();
        final List<String> ids = awards.stream()
                .map(SeasonAwardResult::getSteamId)
                .distinct()
                .toList();
        final Map<String, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getSteamId, u -> u));
        return awards.stream()
                .map(result -> {
                    final User user = users.get(result.getSteamId());
                    return new SeasonController.AwardView(
                            result.getCategory().name(),
                            result.getCategory().getLabel(),
                            result.getPlacementLevel(),
                            result.getSteamId(),
                            userDisplayName(user, result.getSteamId()),
                            user != null ? user.getAvatarFull() : null,
                            user != null ? user.getBlurdataAvatarFull() : null,
                            result.getMetricValue(),
                            result.getTiebreakRoll()
                    );
                })
                .toList();
    }

    SeasonController.SeasonSummaryView mapSummary(final SeasonService.SeasonSummary summary) {
        if (summary == null) {
            return new SeasonController.SeasonSummaryView(0, 0, 0, 0, 0d, 0d, 0d, 0d, 0L, 0, 0, null);
        }
        return new SeasonController.SeasonSummaryView(
                summary.totalPlayers(),
                summary.totalRounds(),
                summary.totalPoints(),
                summary.totalHits(),
                summary.averagePointsPerRound(),
                summary.averagePointsPerPlayer(),
                summary.hitRate(),
                summary.averageActiveDays(),
                summary.longestStreak(),
                summary.durationDays(),
                summary.completedDays(),
                summary.dataThrough()
        );
    }

    List<SeasonController.PlayerHighlight> mapTopPlayers(
            final List<SeasonService.PlayerSeasonStat> playerStats, final int limit) {
        if (playerStats == null || playerStats.isEmpty()) return List.of();
        final int size = Math.min(limit, playerStats.size());
        final List<SeasonService.PlayerSeasonStat> subset = playerStats.subList(0, size);
        final List<String> ids = subset.stream().map(SeasonService.PlayerSeasonStat::steamId).toList();
        final Map<String, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getSteamId, u -> u));
        final List<SeasonController.PlayerHighlight> result = new ArrayList<>(size);
        for (int i = 0; i < subset.size(); i++) {
            final SeasonService.PlayerSeasonStat stat = subset.get(i);
            final User user = users.get(stat.steamId());
            result.add(new SeasonController.PlayerHighlight(
                    i + 1,
                    stat.steamId(),
                    userDisplayName(user, stat.steamId()),
                    user != null ? user.getAvatarFull() : null,
                    user != null ? user.getBlurdataAvatarFull() : null,
                    user != null ? user.getProfileUrl() : null,
                    stat.totalPoints(),
                    stat.rounds(),
                    stat.hits(),
                    stat.flops(),
                    stat.avgPointsPerRound(),
                    stat.avgPointsPerDay(),
                    stat.activeDays(),
                    stat.longestStreak()
            ));
        }
        return result;
    }

    SeasonController.SeasonDailyHighlightsView mapHighlights(
            final SeasonService.SeasonDailyHighlights highlights) {
        if (highlights == null) {
            return new SeasonController.SeasonDailyHighlightsView(null, null, null, null, null);
        }
        return new SeasonController.SeasonDailyHighlightsView(
                mapDailyHighlight(highlights.highestAvg()),
                mapDailyHighlight(highlights.lowestAvg()),
                mapDailyHighlight(highlights.busiest()),
                mapRoundHighlight(highlights.easiestRound()),
                mapRoundHighlight(highlights.hardestRound())
        );
    }

    private SeasonController.DailyHighlightView mapDailyHighlight(
            final SeasonService.SeasonDailyHighlights.DailyHighlight highlight) {
        if (highlight == null) return null;
        return new SeasonController.DailyHighlightView(
                highlight.date(), highlight.avgScore(), highlight.playerCount());
    }

    private SeasonController.RoundHighlightView mapRoundHighlight(
            final SeasonService.SeasonDailyHighlights.RoundHighlight highlight) {
        if (highlight == null) return null;
        return new SeasonController.RoundHighlightView(
                highlight.date(), highlight.roundIndex(), highlight.appId(),
                highlight.appName(), highlight.avgScore(), highlight.playerCount());
    }

    private static String userDisplayName(final User user, final String fallback) {
        if (user == null) return fallback;
        if (user.getPersonaName() != null && !user.getPersonaName().isBlank()) {
            return user.getPersonaName();
        }
        return fallback;
    }
}
