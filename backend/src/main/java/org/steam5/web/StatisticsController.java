package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> indexJson() {
        final Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/stats");
        links.put("genres", "/api/stats/genres");
        links.put("categories", "/api/stats/categories");
        links.put("reviewBuckets", "/api/stats/reviews/buckets");
        links.put("userAchievements", "/api/stats/users/achievements");
        return ResponseEntity.ok(links);
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
    public ResponseEntity<List<StatisticsService.UserLabel>> userAchievements() {
        final List<StatisticsService.UserLabel> result = statisticsService.getUserAchievements();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=604800, max-age=86400, stale-while-revalidate=604800")
                .body(result);
    }
}


