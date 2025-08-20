package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.User;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.UserRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.AuthTokenService;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game")
@Validated
public class ReviewGameStateController {

    private final ReviewGameStateService service;
    private final SteamAppDetailRepository detailRepository;
    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final AuthTokenService authTokenService;
    private final org.steam5.repository.ReviewGamePickRepository pickRepository;

    private static int scorePoints(List<String> buckets, String selected, String actual) {
        int si = buckets.indexOf(selected);
        int ai = buckets.indexOf(actual);
        if (si < 0 || ai < 0) return 0;
        int d = Math.abs(si - ai);
        int max = 5;
        int step = 2;
        return Math.max(0, max - step * d);
    }

    private static Range parseBucketLabel(String label) {
        if (label == null) throw new IllegalArgumentException("label null");
        String s = label.trim();
        // normalize thousands separators and spaces
        s = s.replaceAll("[\\s,._]", "");
        // open ended: "+", "≥", ">="
        if (s.contains("+") || s.contains("≥") || s.contains(">=")) {
            String digits = s.replaceAll("[^0-9]", "");
            long lower = digits.isEmpty() ? 0L : Long.parseLong(digits);
            return new Range(lower, null);
        }
        // closed range: hyphen, en dash, em dash
        final Matcher matcher = Pattern.compile("(?i)(\\d+)[-–—](\\d+)").matcher(s);
        if (matcher.find()) {
            long a = Long.parseLong(matcher.group(1));
            long b = Long.parseLong(matcher.group(2));
            long lower = Math.min(a, b);
            long upper = Math.max(a, b);
            return new Range(lower, upper);
        }
        // single number fallback => exact match
        Matcher one = Pattern.compile("(\\d+)").matcher(s);
        if (one.find()) {
            long v = Long.parseLong(one.group(1));
            return new Range(v, v);
        }
        // default: treat as open ended from zero to be forgiving
        return new Range(0L, null);
    }

    @GetMapping("/days")
    @Cacheable(value = "review-game", key = "'days'", unless = "#result == null")
    public ResponseEntity<List<String>> listDays(@RequestParam(value = "limit", defaultValue = "60") int limit) {
        final int capped = Math.max(1, Math.min(limit, 3650));
        final List<LocalDate> dates = pickRepository.listDistinctPickDates(PageRequest.of(0, capped));
        final List<String> out = dates.stream().map(LocalDate::toString).toList();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=600, max-age=60")
                .body(out);
    }

    @GetMapping("/today/details")
    @Cacheable(value = "review-game", key = "'details'", unless = "#result == null")
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailRepository.findAllByAppIdIn(appIds);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=86400, max-age=3600")
                .body(details);
    }

    @GetMapping("/today")
    @Cacheable(value = "review-game", key = "'picks'", unless = "#result == null")
    public ResponseEntity<ReviewGameStateDto> getToday() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailRepository.findAllByAppIdIn(appIds).stream().toList();
        // Ensure details preserve the appIds order so round indices match what the user sees
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail d : fetched) {
            byId.put(d.getAppId(), d);
        }
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match");
        }

        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=300, max-age=60, stale-while-revalidate=600")
                .body(new ReviewGameStateDto(date, service.getBucketLabels(), service.getBucketTitles(), details));
    }

    @PostMapping("/guess")
    @Cacheable(value = "review-game", key = "#req.appId + ':' + #req.bucketGuess")
    public ResponseEntity<GuessResponse> submitGuess(@RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }

        final int total = service.getTotalReviewCountForApp(req.appId);
        final String actual = service.inferBucket(total);
        final boolean ok = isCorrectForLabel(req.bucketGuess, total);
        return ResponseEntity.ok(new GuessResponse(req.appId, total, actual, ok));
    }

    @GetMapping("/day/{date}")
    @Cacheable(value = "review-game", key = "'picks:' + #date", unless = "#result == null")
    public ResponseEntity<ReviewGameStateDto> getByDate(@PathVariable("date") String date) {
        final java.time.LocalDate day;
        try {
            day = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        final List<ReviewGamePick> picks = pickRepository.findByPickDate(day);
        if (picks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailRepository.findAllByAppIdIn(appIds).stream().toList();
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail d : fetched) byId.put(d.getAppId(), d);
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match for day " + date);
        }
        final boolean isToday = day.equals(java.time.LocalDate.now());
        final String cc = isToday ? "public, s-maxage=300, max-age=60, stale-while-revalidate=600" : "public, max-age=31536000, immutable";
        return ResponseEntity.ok()
                .header("Cache-Control", cc)
                .body(new ReviewGameStateDto(day, service.getBucketLabels(), service.getBucketTitles(), details));
    }

    @GetMapping("/my/today")
    public ResponseEntity<?> myToday(@RequestHeader HttpHeaders headers,
                                     @CookieValue(value = "s5_token", required = false) String cookieToken) {
        final String bearer = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (bearer != null && bearer.toLowerCase().startsWith("bearer ")) {
            token = bearer.substring(7).trim();
        } else if (cookieToken != null && !cookieToken.isBlank()) {
            token = cookieToken;
        }
        final String steamId = token == null ? null : authTokenService.verifyToken(token);
        if (steamId == null) return ResponseEntity.status(401).build();

        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final var guesses = guessRepository.findAllForDay(steamId, date);
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                service.getTotalReviewCountForApp(g.getAppId())
        )).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/buckets")
    @Cacheable(value = "one-day", key = "'buckets'")
    public ResponseEntity<BucketMeta> buckets() {
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=86400, max-age=3600")
                .body(new BucketMeta(service.getBucketLabels(), service.getBucketTitles()));
    }

    public record ReviewGameStateDto(LocalDate date, List<String> buckets, List<String> bucketTitles,
                                     List<SteamAppDetail> picks) {
    }

    public record BucketMeta(List<String> buckets, List<String> bucketTitles) {
    }

    public record GuessRequest(Long appId, String bucketGuess) {
    }

    public record GuessResponse(Long appId, int totalReviews, String actualBucket, boolean correct) {
    }

    public record MyGuessDto(int roundIndex, Long appId, String selectedBucket, String actualBucket, int totalReviews) {
    }

    // --- Bucket label parser: supports ranges like "1-100", en dash, em dash, and open-ended labels like "10000+" or "≥ 10000" ---
    private static boolean isCorrectForLabel(String label, long totalReviews) {
        Range r = parseBucketLabel(label);
        if (r.upper == null) return totalReviews >= r.lower;
        return totalReviews >= r.lower && totalReviews <= r.upper;
    }

    @PostMapping("/guess-auth")
    public ResponseEntity<GuessResponse> submitGuessAuthenticated(@RequestHeader HttpHeaders headers,
                                                                  @CookieValue(value = "s5_token", required = false) String cookieToken,
                                                                  @RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }
        final String bearer = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (bearer != null && bearer.toLowerCase().startsWith("bearer ")) {
            token = bearer.substring(7).trim();
        } else if (cookieToken != null && !cookieToken.isBlank()) {
            token = cookieToken;
        }
        final String steamId = token == null ? null : authTokenService.verifyToken(token);
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        // ensure user exists (id is primary key; handle race with duplicate insert)
        if (!userRepository.existsById(steamId)) {
            try {
                User user = new User();
                user.setSteamId(steamId);
                user.setCreatedAt(OffsetDateTime.now());
                userRepository.save(user);
            } catch (DataIntegrityViolationException ignored) {
                // another request created it concurrently; proceed
            }
        }

        final int total = service.getTotalReviewCountForApp(req.appId);
        final String computedActual = service.inferBucket(total);

        // date = today’s picks date
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final java.time.LocalDate date = picks.isEmpty() ? java.time.LocalDate.now() : picks.getFirst().getPickDate();
        // Compute roundIndex strictly from ordered picks; default to -1 if not found
        final List<Long> pickOrder = picks.stream().map(ReviewGamePick::getAppId).toList();
        final int foundIdx = pickOrder.indexOf(req.appId);
        if (foundIdx < 0) {
            return ResponseEntity.badRequest().body(new GuessResponse(req.appId, total, computedActual, false));
        }
        final int roundIndex = foundIdx + 1;
        final int points = scorePoints(service.getBucketLabels(), req.bucketGuess, computedActual);

        // If exists, return existing without overwriting
        final var existingOpt = guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex);
        if (existingOpt.isPresent()) {
            final var g = existingOpt.get();
            final boolean alreadyOk = g.getActualBucket() != null && g.getActualBucket().equals(g.getSelectedBucket());
            return ResponseEntity.ok(new GuessResponse(g.getAppId(), total, g.getActualBucket(), alreadyOk));
        }

        // create new
        guessRepository.save(new org.steam5.domain.Guess(null, steamId, date, roundIndex, req.appId, req.bucketGuess, computedActual, points, java.time.OffsetDateTime.now()));
        final boolean ok = isCorrectForLabel(req.bucketGuess, total);
        return ResponseEntity.ok(new GuessResponse(req.appId, total, computedActual, ok));
    }

    private record Range(long lower, Long upper) {
    }
}


