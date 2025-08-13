package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.repository.SteamAppReviewsRepository;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-game-metadata")
@Validated
public class MetadataController {

    private final SteamAppReviewsRepository reviewsRepository;

    @GetMapping("/thresholds")
    @Cacheable(value = "one-day", key = "'thresholds'", unless = "#result == null")
    public ResponseEntity<SteamAppReviewsRepository.ReviewThresholds> getThresholds() {
        final SteamAppReviewsRepository.ReviewThresholds thresholds = reviewsRepository.findPercentileThresholds();
        return ResponseEntity.ok(thresholds);
    }

}
