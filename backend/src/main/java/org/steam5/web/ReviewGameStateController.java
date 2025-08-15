package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.steam5.service.SteamAppDetailsFetcher;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game")
@Validated
public class ReviewGameStateController {

    private final ReviewGameStateService service;
    private final SteamAppDetailRepository detailRepository;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final AuthTokenService authTokenService;

    @GetMapping("/today")
    @Cacheable(value = "review-game", key = "'picks'", unless = "#result == null")
    public ResponseEntity<ReviewGameStateDto> getToday() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailRepository.findAllById(appIds).stream().toList();

        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match");
        }

        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        return ResponseEntity.ok(new ReviewGameStateDto(date, service.getBucketLabels(), details));
    }

    @GetMapping("/today/details")
    @Cacheable(value = "review-game", key = "'details'", unless = "#result == null")
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailRepository.findAllById(appIds);
        return ResponseEntity.ok(details);
    }

    private static int scorePoints(java.util.List<String> buckets, String selected, String actual) {
        int si = buckets.indexOf(selected);
        int ai = buckets.indexOf(actual);
        if (si < 0 || ai < 0) return 0;
        int d = Math.abs(si - ai);
        int max = 5;
        int step = 2;
        return Math.max(0, max - step * d);
    }

    @PostMapping("/guess")
    @Cacheable(value = "review-game", key = "#req.appId + ':' + #req.bucketGuess")
    public ResponseEntity<GuessResponse> submitGuess(@RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }

        final int total = service.getTotalReviewCountForApp(req.appId);
        final String actual = service.inferBucket(total);
        final boolean ok = actual.equals(req.bucketGuess);
        return ResponseEntity.ok(new GuessResponse(req.appId, total, actual, ok));
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
                userRepository.save(new User(steamId, null, null, null, null, null, null, null, null, null, null, null, null, OffsetDateTime.now()));
            } catch (DataIntegrityViolationException ignored) {
                // another request created it concurrently; proceed
            }
        }

        final int total = service.getTotalReviewCountForApp(req.appId);
        final String actual = service.inferBucket(total);
        final boolean ok = actual.equals(req.bucketGuess);

        // date = today’s picks date
        final java.util.List<ReviewGamePick> picks = service.generateDailyPicks();
        final java.time.LocalDate date = picks.isEmpty() ? java.time.LocalDate.now() : picks.getFirst().getPickDate();
        final int roundIndex = Math.max(1, picks.stream().map(ReviewGamePick::getAppId).toList().indexOf(req.appId) + 1);
        final int points = scorePoints(service.getBucketLabels(), req.bucketGuess, actual);

        // upsert by (steamId, date, roundIndex)
        guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex).ifPresentOrElse(g -> {
            g.setSelectedBucket(req.bucketGuess);
            g.setActualBucket(actual);
            g.setPoints(points);
            guessRepository.save(g);
        }, () -> {
            guessRepository.save(new org.steam5.domain.Guess(null, steamId, date, roundIndex, req.appId, req.bucketGuess, actual, points, java.time.OffsetDateTime.now()));
        });

        return ResponseEntity.ok(new GuessResponse(req.appId, total, actual, ok));
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
        return ResponseEntity.ok(guesses);
    }

    @GetMapping("/buckets")
    @Cacheable(value = "one-day", key = "'buckets'")
    public ResponseEntity<List<String>> buckets() {
        return ResponseEntity.ok(service.getBucketLabels());
    }

    public record ReviewGameStateDto(LocalDate date, List<String> buckets, List<SteamAppDetail> picks) {
    }

    public record GuessRequest(Long appId, String bucketGuess) {
    }

    public record GuessResponse(Long appId, int totalReviews, String actualBucket, boolean correct) {
    }
}


