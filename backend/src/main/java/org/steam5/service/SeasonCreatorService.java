package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.config.SeasonProperties;
import org.steam5.domain.Season;
import org.steam5.domain.SeasonStatus;
import org.steam5.repository.SeasonRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
class SeasonCreatorService {

    private final SeasonRepository seasonRepository;
    private final SeasonProperties seasonProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Season createSeasonsUntil(LocalDate date) {
        Season last = seasonRepository.findTopByOrderBySeasonNumberDesc().orElse(null);
        if (last == null) {
            LocalDate startDate = date;
            return seasonRepository.save(buildSeason(1, startDate, SeasonStatus.ACTIVE));
        }

        Season current = last;
        int nextNumber = last.getSeasonNumber() + 1;
        LocalDate nextStart = last.getEndDate().plusDays(1);
        while (current.getEndDate().isBefore(date)) {
            current = seasonRepository.save(buildSeason(nextNumber++, nextStart, SeasonStatus.ACTIVE));
            nextStart = current.getEndDate().plusDays(1);
        }
        return current;
    }

    private Season buildSeason(int seasonNumber, LocalDate startDate, SeasonStatus status) {
        return buildSeason(seasonNumber, startDate, status, seasonProperties);
    }

    /**
     * The single definition of what a season row is: its dates, status, award seed and
     * timestamps. Lives here because this service owns season creation; {@code SeasonService}
     * calls it too, so the invariants cannot drift between the two creation paths.
     */
    static Season buildSeason(int seasonNumber, LocalDate startDate, SeasonStatus status,
                              SeasonProperties properties) {
        Season season = new Season();
        season.setSeasonNumber(seasonNumber);
        season.setStartDate(startDate);
        season.setEndDate(startDate.plusDays(seasonLengthDays(properties) - 1L));
        season.setStatus(status);
        season.setAwardSeed(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        season.setAwardsFinalizedAt(null);
        season.setCreatedAt(OffsetDateTime.now());
        season.setUpdatedAt(OffsetDateTime.now());
        return season;
    }

    /** Season length in days, clamped to at least one. */
    static int seasonLengthDays(SeasonProperties properties) {
        return Math.max(1, properties.getLengthDays());
    }
}
