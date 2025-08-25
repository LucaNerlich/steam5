package org.steam5.web;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return getGuessResponse(guesses);
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<LeaderEntry>> weekly(@RequestParam(name = "floating", required = false, defaultValue = "false") boolean floating) {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();

        final LocalDate today = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final LocalDate start;
        final LocalDate end;

        if (floating) {
            // last seven days including today
            end = today;
            start = today.minusDays(6);
        } else {
            // last full week: Monday..Sunday immediately before the current week
            final LocalDate startOfCurrentWeek = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7L);
            start = startOfCurrentWeek.minusDays(7);
            end = startOfCurrentWeek.minusDays(1);
        }

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return getGuessResponse(guesses);
    }

    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<List<LeaderEntry>> allTime() {
        final List<Guess> guesses = guessRepository.findAll();
        return getGuessResponse(guesses);
    }

    @NotNull
    private ResponseEntity<List<LeaderEntry>> getGuessResponse(final List<Guess> guesses) {
        final Map<String, List<Guess>> byUser = guesses.stream().collect(Collectors.groupingBy(Guess::getSteamId));
        final List<LeaderEntry> out = byUser.entrySet().stream().map(this::buildEntries).sorted((a, b) -> Long.compare(b.totalPoints, a.totalPoints)).toList();
        return ResponseEntity.ok(out);
    }

    @NotNull
    private LeaderboardController.LeaderEntry getLeaderEntry(final String steamId, final long totalPoints, final long rounds, final long hits, final long tooHigh, final long tooLow, final double avgPoints, final User user) {
        final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
        final String avatar = user != null && user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : null;
        final String avatarBlurdata = user != null && user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : null;
        final String profileUrl = user != null && user.getProfileUrl() != null && !user.getProfileUrl().isBlank() ? user.getProfileUrl() : null;
        final int streak = calculateStreak(steamId);
        return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, streak, avatar, avatarBlurdata, profileUrl);
    }

    private LeaderEntry buildEntries(Map.Entry<String, List<Guess>> entry) {
        final String steamId = entry.getKey();
        final List<Guess> list = entry.getValue();
        final long totalPoints = list.stream().mapToLong(Guess::getPoints).sum();
        final long rounds = list.size();
        final long hits = list.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
        final long tooHigh = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
        final long tooLow = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
        final double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
        final User user = userRepository.findById(steamId).orElse(null);
        return getLeaderEntry(steamId, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, user);
    }

    private int calculateStreak(String steamId) {
        // Determine the current streak of consecutive days with at least one guess up to today.
        final List<LocalDate> dates = guessRepository.findDistinctDatesUpTo(steamId, LocalDate.now());
        if (dates.isEmpty()) return 0;
        int streak = 0;
        LocalDate expected = LocalDate.now();
        for (LocalDate d : dates) {
            if (d.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                // break on first gap
                break;
            } else {
                // future date shouldn't happen; skip
            }
        }
        return streak;
    }

    public record LeaderEntry(String steamId,
                              String personaName,
                              long totalPoints, long rounds,
                              long hits, long tooHigh, long tooLow,
                              double avgPoints,
                              int streak,
                              String avatar,
                              String avatarBlurdata,
                              String profileUrl) {
    }
}


