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
        Season season = new Season();
        season.setSeasonNumber(seasonNumber);
        season.setStartDate(startDate);
        season.setEndDate(startDate.plusDays(seasonLengthDays() - 1L));
        season.setStatus(status);
        season.setAwardSeed(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        season.setAwardsFinalizedAt(null);
        season.setCreatedAt(OffsetDateTime.now());
        season.setUpdatedAt(OffsetDateTime.now());
        return season;
    }

    private int seasonLengthDays() {
        return Math.max(1, seasonProperties.getLengthDays());
    }
}
