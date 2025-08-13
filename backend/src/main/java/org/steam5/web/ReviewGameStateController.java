package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.SteamAppReviews;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game")
@Validated
public class ReviewGameStateController {

    private final ReviewGameStateService service;
    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppDetailRepository detailRepository;
    private final Environment environment;

    @GetMapping("/today")
    @Cacheable(value = "reviewGameStateTodayPicks", key = "'picks'", unless = "#result == null")
    public ResponseEntity<ReviewGameStateDto> getToday() {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            return ResponseEntity.notFound().build();
        }

        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final Map<Long, SteamAppReviews> idToReviews = reviewsRepository.findAllById(appIds).stream()
                .collect(Collectors.toMap(SteamAppReviews::getAppId, Function.identity()));
        final List<ReviewGamePickDto> dtoList = picks.stream()
                .map(p -> {
                    SteamAppReviews r = idToReviews.get(p.getAppId());
                    final int pos = r != null ? r.getTotalPositive() : 0;
                    final int neg = r != null ? r.getTotalNegative() : 0;
                    return new ReviewGamePickDto(p.getAppId(), pos, neg);
                })
                .toList();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        return ResponseEntity.ok(new ReviewGameStateDto(date, dtoList));
    }

    @GetMapping("/today/details")
    @Cacheable(value = "reviewGameStateTodayDetails", key = "'details'", unless = "#result == null")
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails() {
        final List<ReviewGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(ReviewGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailRepository.findAllById(appIds);
        return ResponseEntity.ok(details);
    }

    public record ReviewGamePickDto(Long appId, int totalPositive, int totalNegative) {
    }

    @PostMapping("/guess")
    @Cacheable(value = "reviewGuessToday", key = "#req.appId + ':' + #req.bucketGuess")
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
    public ResponseEntity<List<String>> buckets() {
        return ResponseEntity.ok(service.getBucketLabels());
    }

    public record ReviewGameStateDto(LocalDate date, List<ReviewGamePickDto> picks) {
    }

    public record GuessRequest(Long appId, String bucketGuess) {
    }

    public record GuessResponse(Long appId, int totalReviews, String actualBucket, boolean correct) {
    }
}


