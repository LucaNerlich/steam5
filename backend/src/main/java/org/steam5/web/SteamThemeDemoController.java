package org.steam5.web;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.details.Category;
import org.steam5.domain.details.Genre;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.service.SteamThemeDemoService;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/steam/themes")
public class SteamThemeDemoController {

    private final SteamThemeDemoService themeDemoService;

    @GetMapping("/demo")
    public ResponseEntity<ThemeDemoResponse> demo(
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        List<SteamAppDetail> details = themeDemoService.sample(genre, category, limit);
        List<DemoGame> games = details.stream()
                .map(detail -> DemoGame.builder()
                        .appId(detail.getAppId())
                        .name(detail.getName())
                        .shortDescription(detail.getShortDescription())
                        .headerImage(detail.getHeaderImage())
                        .genres(detail.getGenres().stream().map(Genre::getDescription).collect(Collectors.toList()))
                        .categories(detail.getCategories().stream().map(Category::getDescription).collect(Collectors.toList()))
                        .build())
                .toList();

        ThemeDemoResponse response = ThemeDemoResponse.builder()
                .requestedGenre(genre)
                .requestedCategory(category)
                .limit(limit)
                .games(games)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/demo/steam")
    public ResponseEntity<SteamRemoteResponse> steamDemo(
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) throws IOException {
        List<SteamThemeDemoService.SteamSpyGame> games = themeDemoService.sampleFromSteam(genre, limit);
        SteamRemoteResponse response = SteamRemoteResponse.builder()
                .requestedGenre(genre)
                .limit(limit)
                .games(games)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/demo/steam/genres")
    public ResponseEntity<Set<String>> steamGenres() {
        return ResponseEntity.ok(themeDemoService.steamSpyGenres());
    }

    @Builder
    private record ThemeDemoResponse(String requestedGenre,
                                     String requestedCategory,
                                     int limit,
                                     List<DemoGame> games) {
    }

    @Builder
    private record DemoGame(Long appId,
                            String name,
                            String shortDescription,
                            String headerImage,
                            List<String> genres,
                            List<String> categories) {
    }

    @Builder
    private record SteamRemoteResponse(String requestedGenre,
                                       int limit,
                                       List<SteamThemeDemoService.SteamSpyGame> games) {
    }
}


