package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.StatisticsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
public class StatisticsController {

    private static final String ACHIEVEMENTS_HOUR = "public, s-maxage=3600, max-age=600";

    private final StatisticsService statisticsService;
    private final PlayerSpotlightService playerSpotlightService;
    private final LeaderboardRefreshStateRepository refreshStateRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> indexJson() {
        final Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/stats");
        links.put("genres", "/api/stats/genres");
        links.put("categories", "/api/stats/categories");
        links.put("reviewBuckets", "/api/stats/reviews/buckets");
        links.put("userAchievements", "/api/stats/users/achievements");
        links.put("gameStatistics", "/api/stats/game");
        links.put("hardestGames", "/api/stats/game/hardest");
        links.put("perfectDays", "/api/stats/game/perfect-days");
        links.put("spotlight", "/api/stats/spotlight/today");
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=3600")
                .body(links);
    }

    @GetMapping(value = "/spotlight/today", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlayerSpotlightService.SpotlightResponse> spotlightToday() {
        return playerSpotlightService.getTodaySpotlight()
                .map(spotlight -> ResponseEntity.ok()
                        .header("Cache-Control", "public, s-maxage=3600, max-age=300")
                        .body(spotlight))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/genres", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.LabelCount>> genres(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        final List<StatisticsService.LabelCount> result = statisticsService.topGenres(Math.max(1, Math.min(limit, 500)));
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=604800, max-age=86400, stale-while-revalidate=604800")
                .body(result);
    }

    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.LabelCount>> categories(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        final List<StatisticsService.LabelCount> result = statisticsService.topCategories(Math.max(1, Math.min(limit, 500)));
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=604800, max-age=86400, stale-while-revalidate=604800")
                .body(result);
    }

    @GetMapping(value = "/reviews/buckets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.Bucket>> reviewBuckets(@RequestParam(name = "mode", defaultValue = "LOG_SPACE") StatisticsService.BucketMode mode) {
        final List<StatisticsService.Bucket> result = statisticsService.reviewBuckets(mode);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=604800, max-age=86400, stale-while-revalidate=604800")
                .body(result);
    }

    @GetMapping(value = "/users/achievements", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.UserLabel>> userAchievements(
            @RequestParam(name = "timeframe", defaultValue = "all") String timeframe) {
        final StatisticsService.Timeframe window = switch (timeframe.toLowerCase()) {
            case "all", "alltime", "all-time" -> StatisticsService.Timeframe.ALL_TIME;
            case "season" -> StatisticsService.Timeframe.SEASON;
            case "monthly" -> StatisticsService.Timeframe.MONTHLY;
            case "weekly" -> StatisticsService.Timeframe.WEEKLY;
            case "daily", "today" -> StatisticsService.Timeframe.DAILY;
            default -> null;
        };
        if (window == null) {
            return ResponseEntity.badRequest().build();
        }
        // One switch decides both the data and its freshness: a second switch over the same
        // labels let an unknown timeframe serve all-time data under the 1-day TTL.
        final UserAchievementsWindow result = switch (window) {
            case SEASON -> new UserAchievementsWindow(statisticsService.getUserAchievementsSeason(), ACHIEVEMENTS_HOUR);
            case MONTHLY -> new UserAchievementsWindow(statisticsService.getUserAchievementsMonthly(), ACHIEVEMENTS_HOUR);
            case WEEKLY -> new UserAchievementsWindow(statisticsService.getUserAchievementsWeekly(), ACHIEVEMENTS_HOUR);
            case DAILY -> new UserAchievementsWindow(statisticsService.getUserAchievementsDaily(), "public, s-maxage=300, max-age=60");
            case ALL_TIME -> new UserAchievementsWindow(statisticsService.getUserAchievements(), "public, s-maxage=86400, max-age=3600");
        };

        return ResponseEntity.ok()
                .header("Cache-Control", result.cacheControl())
                .body(result.labels());
    }

    private record UserAchievementsWindow(List<StatisticsService.UserLabel> labels, String cacheControl) {
    }

    @GetMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
    @Cacheable(value = "stats-hourly",
            key = "'game-statistics-' + T(Math).max(1, T(Math).min(#topGamesLimit, 50))",
            unless = "#result == null || #result.body == null")
    public ResponseEntity<StatisticsService.GameStatistics> gameStatistics(
            @RequestParam(name = "topGamesLimit", defaultValue = "10") int topGamesLimit) {
        // Clamp in both the SpEL cache key (above) and the handler body so
        // arbitrarily many distinct limits cannot churn the cache.
        // @Cacheable must stay on this mapped method: a same-class helper
        // would be a self-invocation and skip the Spring cache proxy.
        final int normalizedTopGamesLimit = Math.max(1, Math.min(topGamesLimit, 50));
        final List<StatisticsService.TopGameByReviews> topGames = statisticsService.getTopGamesByReviewCount(normalizedTopGamesLimit);
        final StatisticsService.DailyAvgScoreStats dailyStats = statisticsService.getDailyAvgScoreStats();
        final StatisticsService.GameStatistics stats = new StatisticsService.GameStatistics(topGames, dailyStats);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600")
                .body(stats);
    }

    @GetMapping(value = "/game/perfect-days", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.PerfectDayEntry>> perfectDays() {
        final List<StatisticsService.PerfectDayEntry> result = statisticsService.getPerfectDays();
        final ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600");
        refreshStateRepository.findById(LeaderboardType.PERFECT_DAYS)
                .ifPresent(state -> builder.header("X-Leaderboard-Refreshed-At", state.getRefreshedAt().toString()));
        return builder.body(result);
    }

    @GetMapping(value = "/game/hardest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.HardestGame>> hardestGames(
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        final int normalizedLimit = Math.max(1, Math.min(limit, 100));
        final List<StatisticsService.HardestGame> result = statisticsService.getHardestGames(normalizedLimit);
        final ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600");
        refreshStateRepository.findById(LeaderboardType.HARDEST_GAMES)
                .ifPresent(state -> builder.header("X-Leaderboard-Refreshed-At", state.getRefreshedAt().toString()));
        return builder.body(result);
    }
}


