package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.Guess;
import org.steam5.domain.User;
import org.steam5.domain.SeasonAwardResult;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController("userProfileController")
@RequiredArgsConstructor
@RequestMapping("/api/profile")
@Validated
public class ProfileController {

    private final UserRepository userRepository;
    private final GuessRepository guessRepository;
    private final SteamAppIndexRepository appIndexRepository;
    private final SeasonService seasonService;

    @GetMapping("/{steamId}")
    public ResponseEntity<?> getProfile(@PathVariable("steamId") String steamId) {
        final User user = userRepository.findById(steamId).orElse(null);
        // If user not found in our DB, return 404 as requested
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }

        final List<Guess> guesses = guessRepository.findAll().stream()
                .filter(g -> Objects.equals(g.getSteamId(), steamId))
                .sorted(Comparator.comparing(Guess::getGameDate).thenComparingInt(Guess::getRoundIndex))
                .toList();

        // Resolve app names in bulk to avoid N+1 lookups
        final Map<Long, String> appNames = guesses.stream()
                .map(Guess::getAppId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids ->
                        appIndexRepository.findAllById(ids).stream().collect(Collectors.toMap(
                                org.steam5.domain.SteamAppIndex::getAppId,
                                org.steam5.domain.SteamAppIndex::getName
                        ))
                ));

        long totalPoints = guesses.stream().mapToLong(Guess::getPoints).sum();
        long rounds = guesses.size();
        long hits = guesses.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
        long tooHigh = guesses.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
        long tooLow = guesses.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
        double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;

        // Group guesses by day for convenience in the UI
        Map<LocalDate, List<Guess>> byDay = guesses.stream()
                .collect(Collectors.groupingBy(Guess::getGameDate));

        final java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        final String personaName = (user.getPersonaName() != null && !user.getPersonaName().isBlank()) ? user.getPersonaName() : user.getSteamId();
        final String avatar = (user.getAvatarFull() != null && !user.getAvatarFull().isBlank()) ? user.getAvatarFull() : user.getAvatar();
        final String avatarBlurdata = (user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank()) ? user.getBlurdataAvatarFull() : user.getBlurdataAvatar();

        out.put("steamId", user.getSteamId());
        out.put("personaName", personaName);
        out.put("avatar", avatar);
        out.put("avatarBlurdata", avatarBlurdata);
        out.put("profileUrl", user.getProfileUrl());
        out.put("stats", Map.of(
                "totalPoints", totalPoints,
                "rounds", rounds,
                "hits", hits,
                "tooHigh", tooHigh,
                "tooLow", tooLow,
                "avgPoints", avgPoints
        ));
        out.put("days", byDay.entrySet().stream()
                        .sorted(Map.Entry.<LocalDate, List<Guess>>comparingByKey().reversed())
                        .map(e -> Map.of(
                                "date", e.getKey().toString(),
                                "rounds", e.getValue().stream().sorted(Comparator.comparingInt(Guess::getRoundIndex)).map(g -> Map.of(
                                        "roundIndex", g.getRoundIndex(),
                                        "appId", g.getAppId(),
                                        "appName", appNames.getOrDefault(g.getAppId(), String.valueOf(g.getAppId())),
                                        "selectedBucket", g.getSelectedBucket(),
                                        "actualBucket", g.getActualBucket(),
                                        "points", g.getPoints()
                                )).toList()
                        ))
                        .toList());

        final List<SeasonAwardResult> awards = seasonService.listAwardsForPlayer(steamId);
        out.put("awards", awards.stream()
                .map(result -> Map.of(
                        "seasonId", result.getSeason().getId(),
                        "seasonNumber", result.getSeason().getSeasonNumber(),
                        "seasonStart", result.getSeason().getStartDate().toString(),
                        "seasonEnd", result.getSeason().getEndDate().toString(),
                        "category", result.getCategory().name(),
                        "categoryLabel", result.getCategory().getLabel(),
                        "placementLevel", result.getPlacementLevel(),
                        "metricValue", result.getMetricValue(),
                        "tiebreakRoll", result.getTiebreakRoll()
                ))
                .toList());

        return ResponseEntity.ok(out);
    }

    private static int bucketOrderFromLabel(String label) {
        if (label == null) return Integer.MIN_VALUE;
        final String s = label.trim();
        try {
            if (s.endsWith("+")) {
                return Integer.parseInt(s.substring(0, s.length() - 1));
            }
            final int dash = s.indexOf('-');
            if (dash > 0) {
                return Integer.parseInt(s.substring(0, dash));
            }
        } catch (Exception ignored) {
        }
        return Integer.MIN_VALUE;
    }
}


