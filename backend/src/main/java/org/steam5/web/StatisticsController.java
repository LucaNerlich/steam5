package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class StatisticsController {

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
        final List<StatisticsService.UserLabel> result = switch (timeframe.toLowerCase()) {
            case "season" -> statisticsService.getUserAchievementsSeason();
            case "monthly" -> statisticsService.getUserAchievementsMonthly();
            case "weekly" -> statisticsService.getUserAchievementsWeekly();
            case "daily", "today" -> statisticsService.getUserAchievementsDaily();
            default -> statisticsService.getUserAchievements();
        };

        final String cacheControl = switch (timeframe.toLowerCase()) {
            case "season" -> "public, s-maxage=3600, max-age=600";  // 1 hour server, 10 min client
            case "monthly" -> "public, s-maxage=3600, max-age=600";  // 1 hour server, 10 min client
            case "weekly" -> "public, s-maxage=3600, max-age=600";  // 1 hour server, 10 min client
            case "daily", "today" -> "public, s-maxage=300, max-age=60";  // 5 min server, 1 min client
            default -> "public, s-maxage=86400, max-age=3600";  // 1 day server, 1 hour client
        };

        // Add server timezone offset header for client-side time conversion
        final int serverOffsetMinutes = java.time.ZoneId.systemDefault()
                .getRules()
                .getOffset(java.time.Instant.now())
                .getTotalSeconds() / 60;

        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .header("X-Server-Timezone-Offset", String.valueOf(serverOffsetMinutes))
                .body(result);
    }

    @GetMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatisticsService.GameStatistics> gameStatistics(
            @RequestParam(name = "topGamesLimit", defaultValue = "10") int topGamesLimit) {
        // Clamp BEFORE the cacheable call so arbitrarily many distinct limits
        // cannot create distinct cache entries (cache churn).
        return gameStatisticsNormalized(Math.max(1, Math.min(topGamesLimit, 50)));
    }

    @Cacheable(value = "stats-hourly", key = "'game-statistics-' + #normalizedTopGamesLimit", unless = "#result == null || #result.body == null")
    public ResponseEntity<StatisticsService.GameStatistics> gameStatisticsNormalized(int normalizedTopGamesLimit) {
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


