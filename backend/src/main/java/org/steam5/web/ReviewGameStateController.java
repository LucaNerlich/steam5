package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SteamAppDetailsFetcher;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game")
@Validated
public class ReviewGameStateController {

    private final ReviewGameStateService service;
    private final SteamAppDetailRepository detailRepository;
    private final SteamAppDetailsFetcher detailsFetcher;

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


