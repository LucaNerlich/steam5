package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.steam5.domain.GameDate;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.domain.SeasonStatus;
import org.steam5.service.SeasonService;

import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seasons")
@Validated
public class SeasonController {

    private final SeasonService seasonService;
    private final SeasonResponseMapper mapper;
    private static final int SEASON_TOP_PLAYERS_LIMIT = 15;

    @GetMapping("/current")
    @Cacheable(value = "season-current-response", key = "'current'", unless = "#result == null || #result.body == null")
    public ResponseEntity<CurrentSeasonResponse> currentSeason() {
        LocalDate todayUtc = GameDate.todayUtc();
        Season season = seasonService.findSeasonContaining(todayUtc)
                .orElseGet(() -> seasonService.ensureSeasonForDate(todayUtc));
        List<SeasonAwardResult> awards = season.getStatus() == SeasonStatus.FINALIZED
                ? seasonService.listAwardsForSeason(season.getId())
                : List.of();
        SeasonView view = mapper.mapSeason(season, awards);
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(todayUtc, season.getEndDate()) + 1);
        CurrentSeasonResponse response = new CurrentSeasonResponse(
                view,
                todayUtc,
                daysRemaining,
                season.getEndDate().plusDays(1)
        );
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=900, max-age=300")
                .body(response);
    }

    @GetMapping
    @Cacheable(value = "season-list-response", key = "'limit:' + #limit + ':include:' + #includeCurrent", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<SeasonView>> seasons(
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestParam(name = "includeCurrent", defaultValue = "false") boolean includeCurrent) {
        final int normalizedLimit = Math.max(1, Math.min(limit, 25));
        List<Season> seasons = seasonService.listSeasonsDescending();
        List<SeasonView> response = new ArrayList<>();
        for (Season season : seasons) {
            if (season.getStatus() != SeasonStatus.FINALIZED && !includeCurrent) {
                continue;
            }
            List<SeasonAwardResult> awards = season.getStatus() == SeasonStatus.FINALIZED
                    ? seasonService.listAwardsForSeason(season.getId())
                    : List.of();
            response.add(mapper.mapSeason(season, awards));
            if (response.size() >= normalizedLimit) break;
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=1800, max-age=600")
                .body(response);
    }

    @GetMapping("/{seasonIdentifier}")
    @Cacheable(value = "season-detail-response", key = "#seasonIdentifier", unless = "#result == null || #result.body == null")
    public ResponseEntity<SeasonDetailView> seasonDetail(@PathVariable String seasonIdentifier) {
        Season season = resolveSeason(seasonIdentifier);
        List<SeasonAwardResult> awards = season.getStatus() == SeasonStatus.FINALIZED
                ? seasonService.listAwardsForSeason(season.getId())
                : List.of();
        SeasonView seasonView = mapper.mapSeason(season, awards);
        SeasonService.SeasonReport report = seasonService.buildSeasonReport(season);
        SeasonSummaryView summary = mapper.mapSummary(report.summary());
        List<PlayerHighlight> players = mapper.mapTopPlayers(report.players(), SEASON_TOP_PLAYERS_LIMIT);
        SeasonDailyHighlightsView highlights = mapper.mapHighlights(seasonService.buildSeasonHighlights(season));
        SeasonDetailView view = new SeasonDetailView(seasonView, summary, players, highlights);
        LocalDate today = GameDate.todayUtc();
        boolean isCurrentSeason = !today.isBefore(season.getStartDate()) && !today.isAfter(season.getEndDate());
        String cacheControl = isCurrentSeason
                ? "public, s-maxage=900, max-age=300"
                : "public, s-maxage=604800, max-age=86400";
        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .body(view);
    }

    @GetMapping("/{seasonId}/awards")
    @Cacheable(value = "season-awards-response", key = "#seasonId", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<AwardView>> awards(@PathVariable Long seasonId) {
        Season season = seasonService.findSeason(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        List<SeasonAwardResult> awards = seasonService.listAwardsForSeason(season.getId());
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=900")
                .body(mapper.mapAwards(awards));
    }

    private Season resolveSeason(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season identifier is required");
        }
        try {
            int seasonNumber = Integer.parseInt(identifier);
            Optional<Season> byNumber = seasonService.findSeasonByNumber(seasonNumber);
            if (byNumber.isPresent()) {
                return byNumber.get();
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            long seasonId = Long.parseLong(identifier);
            return seasonService.findSeason(seasonId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found");
        }
    }

    public record CurrentSeasonResponse(SeasonView season,
                                        LocalDate today,
                                        long daysRemaining,
                                        LocalDate nextSeasonStart) {
    }

    public record SeasonView(Long id,
                             int seasonNumber,
                             LocalDate startDate,
                             LocalDate endDate,
                             SeasonStatus status,
                             java.time.OffsetDateTime awardsFinalizedAt,
                             List<AwardView> awards) {
    }

    public record AwardView(String category,
                            String categoryLabel,
                            int placementLevel,
                            String steamId,
                            String personaName,
                            String avatar,
                            String avatarBlurHash,
                            long metricValue,
                            Integer tiebreakRoll) {
    }

    public record SeasonDetailView(SeasonView season,
                                   SeasonSummaryView summary,
                                   List<PlayerHighlight> topPlayers,
                                   SeasonDailyHighlightsView highlights) {
    }

    public record SeasonSummaryView(long totalPlayers,
                                    long totalRounds,
                                    long totalPoints,
                                    long totalHits,
                                    double averagePointsPerRound,
                                    double averagePointsPerPlayer,
                                    double hitRate,
                                    double averageActiveDays,
                                    long longestStreak,
                                    int durationDays,
                                    int completedDays,
                                    LocalDate dataThrough) {
    }

    public record PlayerHighlight(int rank,
                                  String steamId,
                                  String personaName,
                                  String avatar,
                                  String avatarBlurHash,
                                  String profileUrl,
                                  long totalPoints,
                                  long rounds,
                                  long hits,
                                  long flops,
                                  double avgPointsPerRound,
                                  double avgPointsPerDay,
                                  long activeDays,
                                  long longestStreak) {
    }

    public record SeasonDailyHighlightsView(DailyHighlightView highestAvg,
                                            DailyHighlightView lowestAvg,
                                            DailyHighlightView busiest,
                                            RoundHighlightView easiestRound,
                                            RoundHighlightView hardestRound) {
    }

    public record DailyHighlightView(LocalDate date,
                                     double avgScore,
                                     long playerCount) {
    }

    public record RoundHighlightView(LocalDate date,
                                     int roundIndex,
                                     long appId,
                                     String appName,
                                     double avgScore,
                                     long playerCount) {
    }
}


