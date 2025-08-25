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
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;

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

        long totalPoints = guesses.stream().mapToLong(Guess::getPoints).sum();
        long rounds = guesses.size();
        long hits = guesses.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
        long tooHigh = guesses.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
        long tooLow = guesses.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
        double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;

        // Group guesses by day for convenience in the UI
        Map<LocalDate, List<Guess>> byDay = guesses.stream()
                .collect(Collectors.groupingBy(Guess::getGameDate));

        return ResponseEntity.ok(Map.of(
                "steamId", user.getSteamId(),
                "personaName", user.getPersonaName(),
                "avatar", user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : user.getAvatar(),
                "avatarBlurdata", user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : user.getBlurdataAvatar(),
                "profileUrl", user.getProfileUrl(),
                "stats", Map.of(
                        "totalPoints", totalPoints,
                        "rounds", rounds,
                        "hits", hits,
                        "tooHigh", tooHigh,
                        "tooLow", tooLow,
                        "avgPoints", avgPoints
                ),
                "days", byDay.entrySet().stream()
                        .sorted(Map.Entry.<LocalDate, List<Guess>>comparingByKey().reversed())
                        .map(e -> Map.of(
                                "date", e.getKey().toString(),
                                "rounds", e.getValue().stream().sorted(Comparator.comparingInt(Guess::getRoundIndex)).map(g -> Map.of(
                                        "roundIndex", g.getRoundIndex(),
                                        "appId", g.getAppId(),
                                        "selectedBucket", g.getSelectedBucket(),
                                        "actualBucket", g.getActualBucket(),
                                        "points", g.getPoints()
                                )).toList()
                        ))
                        .toList()
        ));
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


