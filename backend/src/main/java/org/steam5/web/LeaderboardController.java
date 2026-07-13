package org.steam5.web;

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
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;
    private final LeaderboardService leaderboardService;

    @GetMapping("/today")
    @Cacheable(value = "leaderboard-live", key = "'today:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> today() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final List<Guess> guesses = guessRepository.findAllByDate(date);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, date));
    }

    @GetMapping("/weekly")
    @Cacheable(value = "leaderboard-static", key = "'weekly:' + #floating + ':' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> weekly(@RequestParam(name = "floating", required = false, defaultValue = "false") boolean floating) {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();

        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
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
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    @GetMapping("/monthly")
    @Cacheable(value = "leaderboard-static", key = "'monthly:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        // Last 30 days including today
        final LocalDate start = today.minusDays(29);
        final LocalDate end = today;

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    @GetMapping("/season")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> season() {
        final LocalDate today = GameDate.todayUtc();
        final Season season = seasonService.findSeasonContaining(today)
                .orElseGet(() -> seasonService.ensureSeasonForDate(today));
        final LocalDate asOfDate = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        final String cacheKey = "season:" + season.getSeasonNumber() + ":" + asOfDate;
        final Cache cache = cacheManager.getCache("leaderboard-static");
        if (cache != null) {
            final Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<LeaderboardService.LeaderEntry> cachedEntries = (List<LeaderboardService.LeaderEntry>) cached;
                return ResponseEntity.ok(cachedEntries);
            }
        }

        final List<Guess> guesses = guessRepository.findAllBetween(season.getStartDate(), asOfDate);
        final ResponseEntity<List<LeaderboardService.LeaderEntry>> response = ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, asOfDate));
        if (cache != null && response.getBody() != null) {
            cache.put(cacheKey, response.getBody());
        }
        return response;
    }

    @GetMapping(value = {"", "/", "/all"})
    @Cacheable(value = "leaderboard-static", key = "'all-time:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> allTime() {
        final LocalDate today = GameDate.todayUtc();
        return ResponseEntity.ok(leaderboardService.buildAllTimeLeaderboard(today));
    }
}


