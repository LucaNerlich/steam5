package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.repository.details.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/metrics")
@Validated
public class MetricsController {

    private final SteamAppIndexRepository indexRepository;
    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppDetailRepository detailRepository;
    private final DeveloperRepository developerRepository;
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final PublisherRepository publisherRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ReviewGamePickRepository pickRepository;
    private final IngestStateRepository ingestStateRepository;

    @GetMapping("/counts")
    @Cacheable(value = "one-hour", key = "'counts'", unless = "#result == null")
    public ResponseEntity<EntityCounts> getEntityCounts() {
        final EntityCounts counts = new EntityCounts(
                indexRepository.count(),
                reviewsRepository.count(),
                detailRepository.count(),
                developerRepository.count(),
                genreRepository.count(),
                movieRepository.count(),
                publisherRepository.count(),
                screenshotRepository.count(),
                pickRepository.count(),
                ingestStateRepository.count()
        );
        return ResponseEntity.ok(counts);
    }

    public record EntityCounts(
            long steamAppIndexCount,
            long steamAppReviewsCount,
            long steamAppDetailCount,
            long developerCount,
            long genreCount,
            long movieCount,
            long publisherCount,
            long screenshotCount,
            long reviewGamePickCount,
            long ingestStateCount
    ) {
    }
}


