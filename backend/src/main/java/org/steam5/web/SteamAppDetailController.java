package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.SteamAppIndexRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/details")
@Validated
public class SteamAppDetailController {

    private final SteamAppDetailRepository detailRepository;
    private final SteamAppIndexRepository indexRepository;

    @GetMapping("/{appId}")
    @Transactional(readOnly = true)
    @Cacheable(value = "one-day", key = "#appId", unless = "#result == null || !#result.statusCode.is2xxSuccessful()")
    public ResponseEntity<SteamAppDetail> getDetails(@PathVariable("appId") Long appId) {
        final Optional<SteamAppDetail> detailOpt = detailRepository.findById(appId);
        if (detailOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final SteamAppDetail detail = detailOpt.get();
        // Touch associations to ensure they are initialized within the transaction
        if (detail.getDevelopers() != null) detail.getDevelopers().size();
        if (detail.getPublisher() != null) detail.getPublisher().size();
        if (detail.getCategories() != null) detail.getCategories().size();
        if (detail.getGenres() != null) detail.getGenres().size();
        if (detail.getScreenshots() != null) detail.getScreenshots().size();
        if (detail.getMovies() != null) detail.getMovies().size();
        detail.getPriceOverview();

        return ResponseEntity.ok(detail);
    }

}


