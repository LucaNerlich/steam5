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
import org.steam5.domain.Season;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.domain.SeasonStatus;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seasons")
@Validated
public class SeasonController {

    private final SeasonService seasonService;
    private final UserRepository userRepository;

    @GetMapping("/current")
    @Cacheable(value = "season-current-response", key = "'current'", unless = "#result == null || #result.body == null")
    public ResponseEntity<CurrentSeasonResponse> currentSeason() {
        LocalDate todayUtc = todayUtc();
        Season season = seasonService.findSeasonContaining(todayUtc)
                .orElseGet(() -> seasonService.ensureSeasonForDate(todayUtc));
        List<SeasonAwardResult> awards = season.getStatus() == SeasonStatus.FINALIZED
                ? seasonService.listAwardsForSeason(season.getId())
                : List.of();
        SeasonView view = mapSeason(season, awards);
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
            response.add(mapSeason(season, awards));
            if (response.size() >= normalizedLimit) break;
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=1800, max-age=600")
                .body(response);
    }

    @GetMapping("/{seasonId}/awards")
    @Cacheable(value = "season-awards-response", key = "#seasonId", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<AwardView>> awards(@PathVariable Long seasonId) {
        Season season = seasonService.findSeason(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        List<SeasonAwardResult> awards = seasonService.listAwardsForSeason(season.getId());
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=900")
                .body(mapAwards(awards));
    }

    private SeasonView mapSeason(Season season, List<SeasonAwardResult> awards) {
        List<AwardView> awardViews = mapAwards(awards);
        return new SeasonView(
                season.getId(),
                season.getSeasonNumber(),
                season.getStartDate(),
                season.getEndDate(),
                season.getStatus(),
                season.getAwardsFinalizedAt(),
                awardViews
        );
    }

    private List<AwardView> mapAwards(List<SeasonAwardResult> awards) {
        if (awards == null || awards.isEmpty()) {
            return List.of();
        }
        List<String> ids = awards.stream()
                .map(SeasonAwardResult::getSteamId)
                .distinct()
                .toList();
        Map<String, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getSteamId, user -> user));

        return awards.stream()
                .map(result -> {
                    User user = users.get(result.getSteamId());
                    return new AwardView(
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

    private static String userDisplayName(User user, String fallback) {
        if (user == null) return fallback;
        if (user.getPersonaName() != null && !user.getPersonaName().isBlank()) {
            return user.getPersonaName();
        }
        return fallback;
    }

    private static LocalDate todayUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
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
}


