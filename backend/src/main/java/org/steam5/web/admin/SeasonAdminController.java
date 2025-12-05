package org.steam5.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.steam5.domain.Season;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/admin/seasons")
@RequiredArgsConstructor
public class SeasonAdminController {

    private final SeasonService seasonService;

    @PostMapping("/backfill")
    public ResponseEntity<BackfillResponse> backfill(@RequestBody(required = false) BackfillRequest request) {
        BackfillRequest effective = request == null ? new BackfillRequest(null, null, false) : request;
        List<Season> created;
        if (effective.startDate() != null && effective.endDate() != null) {
            created = seasonService.backfillRange(effective.startDate(), effective.endDate());
        } else {
            created = seasonService.backfillHistoricalSeasons();
        }

        int finalized = 0;
        if (effective.finalizeClosedSeasons()) {
            finalized = finalizeClosedSeasons(created);
        }

        List<Long> ids = created.stream().map(Season::getId).toList();
        BackfillResponse response = new BackfillResponse(ids.size(), ids, finalized);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{seasonId}/finalize")
    public ResponseEntity<Season> finalizeSeason(@PathVariable Long seasonId) {
        Season season = seasonService.findSeason(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        Season finalized = seasonService.finalizeSeason(season);
        return ResponseEntity.ok(finalized);
    }

    private int finalizeClosedSeasons(List<Season> seasons) {
        int count = 0;
        LocalDate todayUtc = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        for (Season season : seasons) {
            if (!season.getEndDate().isAfter(todayUtc)) {
                seasonService.finalizeSeason(season);
                count++;
            }
        }
        return count;
    }

    public record BackfillRequest(LocalDate startDate,
                                  LocalDate endDate,
                                  boolean finalizeClosedSeasons) {
    }

    public record BackfillResponse(int createdSeasons,
                                   List<Long> seasonIds,
                                   int finalizedSeasons) {
    }
}


