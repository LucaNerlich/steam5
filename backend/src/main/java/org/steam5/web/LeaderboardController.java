package org.steam5.web;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final UserRepository userRepository;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;

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
    @Cacheable(value = "leaderboard-live", key = "'today:' + T(java.time.LocalDate).now()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderEntry>> today() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final List<Guess> guesses = guessRepository.findAllByDate(date);
        return getGuessResponse(guesses, date);
    }

    @GetMapping("/weekly")
    @Cacheable(value = "leaderboard-static", key = "'weekly:' + #floating + ':' + T(java.time.LocalDate).now()", unless = "#result == null || #result.body == null")
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
        return getGuessResponse(guesses, today);
    }

    @GetMapping("/monthly")
    @Cacheable(value = "leaderboard-static", key = "'monthly:' + T(java.time.LocalDate).now()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        
        // Last 30 days including today
        final LocalDate start = today.minusDays(29);
        final LocalDate end = today;

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return getGuessResponse(guesses, today);
    }

    @GetMapping("/season")
    public ResponseEntity<List<LeaderEntry>> season() {
        final LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        final Season season = seasonService.findSeasonContaining(today)
                .orElseGet(() -> seasonService.ensureSeasonForDate(today));
        final LocalDate asOfDate = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        final String cacheKey = "season:" + season.getSeasonNumber() + ":" + asOfDate;
        final Cache cache = cacheManager.getCache("leaderboard-static");
        if (cache != null) {
            final Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<LeaderEntry> cachedEntries = (List<LeaderEntry>) cached;
                return ResponseEntity.ok(cachedEntries);
            }
        }

        final List<Guess> guesses = guessRepository.findAllBetween(season.getStartDate(), asOfDate);
        final ResponseEntity<List<LeaderEntry>> response = getGuessResponse(guesses, asOfDate);
        if (cache != null && response.getBody() != null) {
            cache.put(cacheKey, response.getBody());
        }
        return response;
    }

    @GetMapping(value = {"", "/", "/all"})
    @Cacheable(value = "leaderboard-static", key = "'all-time:' + T(java.time.LocalDate).now()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderEntry>> allTime() {
        final List<Guess> guesses = guessRepository.findAll();
        return getGuessResponse(guesses, LocalDate.now());
    }

    @NotNull
    private ResponseEntity<List<LeaderEntry>> getGuessResponse(final List<Guess> guesses, final LocalDate asOfDate) {
        final Map<String, List<Guess>> byUser = guesses.stream().collect(Collectors.groupingBy(Guess::getSteamId));
        final Set<String> steamIds = byUser.keySet();
        if (steamIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        final Map<String, User> usersById = userRepository.findAllById(steamIds).stream()
                .collect(Collectors.toMap(User::getSteamId, user -> user));

        final Map<String, List<LocalDate>> streakDatesById = guessRepository.findDistinctDatesUpToForUsers(List.copyOf(steamIds), asOfDate)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        final List<LeaderEntry> out = byUser.entrySet().stream()
                .map(entry -> buildEntries(entry, usersById, streakDatesById, asOfDate))
                .sorted((a, b) -> Long.compare(b.totalPoints, a.totalPoints))
                .toList();
        return ResponseEntity.ok(out);
    }

    @NotNull
    private LeaderboardController.LeaderEntry getLeaderEntry(final String steamId, final long totalPoints, final long rounds, final long hits, final long tooHigh, final long tooLow, final double avgPoints, final int streak, final User user) {
        final String personaName = user != null && user.getPersonaName() != null && !user.getPersonaName().isBlank() ? user.getPersonaName() : steamId;
        final String avatar = user != null && user.getAvatarFull() != null && !user.getAvatarFull().isBlank() ? user.getAvatarFull() : null;
        final String avatarBlurdata = user != null && user.getBlurdataAvatarFull() != null && !user.getBlurdataAvatarFull().isBlank() ? user.getBlurdataAvatarFull() : null;
        final String profileUrl = user != null && user.getProfileUrl() != null && !user.getProfileUrl().isBlank() ? user.getProfileUrl() : null;
        return new LeaderEntry(steamId, personaName, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, streak, avatar, avatarBlurdata, profileUrl);
    }

    private LeaderEntry buildEntries(final Map.Entry<String, List<Guess>> entry,
                                     final Map<String, User> usersById,
                                     final Map<String, List<LocalDate>> streakDatesById,
                                     final LocalDate asOfDate) {
        final String steamId = entry.getKey();
        final List<Guess> list = entry.getValue();
        final long totalPoints = list.stream().mapToLong(Guess::getPoints).sum();
        final long rounds = list.size();
        final long hits = list.stream().filter(g -> g.getSelectedBucket().equals(g.getActualBucket())).count();
        final long tooHigh = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) > bucketOrderFromLabel(g.getActualBucket())).count();
        final long tooLow = list.stream().filter(g -> bucketOrderFromLabel(g.getSelectedBucket()) < bucketOrderFromLabel(g.getActualBucket())).count();
        final double avgPoints = rounds > 0 ? ((double) totalPoints) / rounds : 0.0;
        final User user = usersById.get(steamId);
        final List<LocalDate> dates = streakDatesById.getOrDefault(steamId, List.of());
        final int streak = calculateStreak(dates, asOfDate);
        return getLeaderEntry(steamId, totalPoints, rounds, hits, tooHigh, tooLow, avgPoints, streak, user);
    }

    private int calculateStreak(final List<LocalDate> dates, final LocalDate asOfDate) {
        // Determine the current streak of consecutive days with at least one guess up to asOfDate.
        if (dates.isEmpty()) return 0;
        int streak = 0;
        LocalDate expected = asOfDate;
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


