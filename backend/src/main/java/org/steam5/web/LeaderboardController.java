package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.repository.GuessRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;

    @GetMapping("/today")
    public ResponseEntity<List<LeaderEntry>> today() {
        final var picks = reviewGameStateService.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? LocalDate.now() : picks.getFirst().getPickDate();
        final List<GuessRepository.LeaderboardRow> rows = guessRepository.leaderboardForDay(date);
        final List<LeaderEntry> out = rows.stream()
                .map(r -> new LeaderEntry(r.getSteamId(), r.getPersonaName(), r.getTotalPoints() == null ? 0 : r.getTotalPoints(), r.getRounds() == null ? 0 : r.getRounds()))
                .toList();
        return ResponseEntity.ok(out);
    }

    public record LeaderEntry(String steamId, String personaName, long totalPoints, long rounds) {
    }
}


