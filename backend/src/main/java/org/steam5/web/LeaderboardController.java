package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final UserRepository userRepository;

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

    @GetMapping("/today")
    public ResponseEntity<List<LeaderEntry>> today() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final List<Guess> guesses = guessRepository.findAllByDate(date);
        final Map<String, List<Guess>> byUser = guesses.stream().collect(Collectors.groupingBy(Guess::getSteamId));
        final List<LeaderEntry> out = byUser.entrySet().stream().map(e -> {
            final String steamId = e.getKey();
            final List<Guess> list = e.getValue();
            final long totalPoints = list.stream().mapToLong(Guess::getPoints).sum();
            final long rounds = list.size();
            final long hits = list.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
            final long tooHigh = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
            final long tooLow = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
            final double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
            final User user = userRepository.findById(steamId).orElse(null);
            final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
            final String avatar = user != null && user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : null;
            final String avatarBlurdata = user != null && user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : null;
            final String profileUrl = user != null && user.getProfileUrl() != null && !user.getProfileUrl().isBlank() ? user.getProfileUrl() : null;
            return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, avatar, avatarBlurdata, profileUrl);
        }).sorted((a, b) -> Long.compare(b.totalPoints, a.totalPoints)).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<List<LeaderEntry>> allTime() {
        final var guesses = guessRepository.findAll();
        final java.util.Map<String, java.util.List<Guess>> byUser = guesses.stream().collect(java.util.stream.Collectors.groupingBy(Guess::getSteamId));
        final List<LeaderEntry> out = byUser.entrySet().stream().map(e -> {
            final String steamId = e.getKey();
            final var list = e.getValue();
            long totalPoints = list.stream().mapToLong(Guess::getPoints).sum();
            long rounds = list.size();
            long hits = list.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
            long tooHigh = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
            long tooLow = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
            double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
            final var user = userRepository.findById(steamId).orElse(null);
            final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
            final String avatar = user != null && user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : null;
            final String avatarBlurdata = user != null && user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : null;
            final String profileUrl = user != null && user.getProfileUrl() != null && !user.getProfileUrl().isBlank() ? user.getProfileUrl() : null;
            return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, avatar, avatarBlurdata, profileUrl);
        }).sorted((a, b) -> Long.compare(b.totalPoints, a.totalPoints)).toList();
        return ResponseEntity.ok(out);
    }

    public record LeaderEntry(String steamId, String personaName,
                              long totalPoints, long rounds,
                              long hits, long tooHigh, long tooLow,
                              double avgPoints,
                              String avatar,
                              String avatarBlurdata,
                              String profileUrl) {
    }
}


