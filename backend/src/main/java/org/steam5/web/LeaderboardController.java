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

    /**
     * Builds the leaderboard for the current review game date.
     *
     * @return the leaderboard entries for the current review game date
     */
    @GetMapping("/today")
    @Cacheable(value = "leaderboard-live", key = "'today:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> today() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final List<Guess> guesses = guessRepository.findAllByDate(date);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, date));
    }

    /**
     * Builds the weekly leaderboard for either the current rolling period or the previous full week.
     *
     * @param floating whether to include the seven days ending on the current date (served from
     *                 {@code mv_leaderboard_weekly}); otherwise, uses the Monday-through-Sunday week
     *                 immediately before the current week, computed live (not MV-backed — its window
     *                 doesn't match the MV's rolling definition)
     * @return leaderboard entries for the selected period
     */
    @GetMapping("/weekly")
    @Cacheable(value = "leaderboard-static", key = "'weekly:' + #floating + ':' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> weekly(@RequestParam(name = "floating", required = false, defaultValue = "false") boolean floating) {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        if (floating) {
            return ResponseEntity.ok(leaderboardService.buildWeeklyLeaderboard(today));
        }

        // last full week: Monday..Sunday immediately before the current week
        final LocalDate startOfCurrentWeek = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7L);
        final LocalDate start = startOfCurrentWeek.minusDays(7);
        final LocalDate end = startOfCurrentWeek.minusDays(1);

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    /**
     * Builds the leaderboard for the 30-day period ending on the current game date, served from
     * {@code mv_leaderboard_monthly}.
     *
     * @return the leaderboard entries for the last 30 days, including the current game date
     */
    @GetMapping("/monthly")
    @Cacheable(value = "leaderboard-static", key = "'monthly:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        return ResponseEntity.ok(leaderboardService.buildMonthlyLeaderboard(today));
    }

    /**
     * Builds the leaderboard for the current season through the current date or the season end date,
     * served from {@code mv_leaderboard_season}.
     *
     * @return the season leaderboard entries
     */
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

        final List<LeaderboardService.LeaderEntry> entries = leaderboardService.buildSeasonLeaderboard(asOfDate);
        final ResponseEntity<List<LeaderboardService.LeaderEntry>> response = ResponseEntity.ok(entries);
        if (cache != null) {
            cache.put(cacheKey, entries);
        }
        return response;
    }

    /**
     * Retrieves the all-time leaderboard as of the current UTC date.
     *
     * @return the all-time leaderboard entries
     */
    @GetMapping(value = {"", "/", "/all"})
    @Cacheable(value = "leaderboard-static", key = "'all-time:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> allTime() {
        final LocalDate today = GameDate.todayUtc();
        return ResponseEntity.ok(leaderboardService.buildAllTimeLeaderboard(today));
    }
}


