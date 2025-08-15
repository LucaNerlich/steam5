package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.Guess;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final UserRepository userRepository;

    @GetMapping("/today")
    public ResponseEntity<List<LeaderEntry>> today() {
        final var picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final var guesses = guessRepository.findAllByDate(date);
        final java.util.Map<String, java.util.List<Guess>> byUser = guesses.stream().collect(java.util.stream.Collectors.groupingBy(Guess::getSteamId));
        final List<LeaderEntry> out = byUser.entrySet().stream().map(e -> {
            final String steamId = e.getKey();
            final var list = e.getValue();
            long totalPoints = list.stream().mapToLong(Guess::getPoints).sum();
            long rounds = list.size();
            long hits = list.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
            long tooHigh = list.stream().filter(g -> {
                final java.util.List<String> buckets = reviewGameStateService.getBucketLabels();
                return buckets.indexOf(g.getSelectedBucket()) > buckets.indexOf(g.getActualBucket());
            }).count();
            long tooLow = list.stream().filter(g -> {
                final java.util.List<String> buckets = reviewGameStateService.getBucketLabels();
                return buckets.indexOf(g.getSelectedBucket()) < buckets.indexOf(g.getActualBucket());
            }).count();
            double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
            final String personaName = userRepository.findById(steamId)
                    .map(u -> u.getPersonaName() != null && !u.getPersonaName().isBlank() ? u.getPersonaName() : u.getSteamId())
                    .orElse(steamId);
            return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints);
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
            long tooHigh = list.stream().filter(g -> {
                final java.util.List<String> buckets = reviewGameStateService.getBucketLabels();
                return buckets.indexOf(g.getSelectedBucket()) > buckets.indexOf(g.getActualBucket());
            }).count();
            long tooLow = list.stream().filter(g -> {
                final java.util.List<String> buckets = reviewGameStateService.getBucketLabels();
                return buckets.indexOf(g.getSelectedBucket()) < buckets.indexOf(g.getActualBucket());
            }).count();
            double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
            final String personaName = userRepository.findById(steamId)
                    .map(u -> u.getPersonaName() != null && !u.getPersonaName().isBlank() ? u.getPersonaName() : u.getSteamId())
                    .orElse(steamId);
            return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints);
        }).sorted((a, b) -> Long.compare(b.totalPoints, a.totalPoints)).toList();
        return ResponseEntity.ok(out);
    }

    public record LeaderEntry(String steamId, String personaName,
                              long totalPoints, long rounds,
                              long hits, long tooHigh, long tooLow,
                              double avgPoints) {
    }
}


