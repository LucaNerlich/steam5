package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.config.ReviewGameConfig;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.SteamAppReviewsRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewGameStateService {

    private final SteamAppReviewsRepository reviewsRepository;
    private final ReviewGamePickRepository pickRepository;
    private final ReviewGameConfig config;

    @Transactional
    public List<ReviewGamePick> generateDailyPicks() {
        final LocalDate today = LocalDate.now();
        final List<ReviewGamePick> existing = pickRepository.findByPickDate(today);
        if (!existing.isEmpty()) {
            return existing;
        }

        // Exclusion window parameterized
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500 ? LocalDate.of(1970, 1, 1) : today.minusDays(doNotRepeatDays);
        final LocalDate includeAll = LocalDate.of(1970, 1, 1); // relax by allowing any past picks

        var thresholds = reviewsRepository.findPercentileThresholds();
        int lowThreshold = thresholds == null || thresholds.getLowThreshold() == null ? 100 : thresholds.getLowThreshold();
        int highThreshold = thresholds == null || thresholds.getHighThreshold() == null ? 10000 : thresholds.getHighThreshold();

        final List<ReviewGamePick> picks = new ArrayList<>(5);
        final Set<Long> chosenIds = new HashSet<>();

        // LOW
        List<Long> lowIds = reviewsRepository.findRandomLowAppIds(excludeSince, lowThreshold, PageRequest.of(0, 3));
        if (!lowIds.isEmpty()) {
            Long id = lowIds.get(0);
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, Category.LOW.name(), OffsetDateTime.now()));
        } else {
            // relax: allow from all lows ignoring recent
            List<Long> relaxed = reviewsRepository.findRandomLowAppIds(includeAll, lowThreshold, PageRequest.of(0, 3));
            if (!relaxed.isEmpty()) {
                Long id = relaxed.get(0);
                chosenIds.add(id);
                picks.add(new ReviewGamePick(null, today, id, Category.LOW.name(), OffsetDateTime.now()));
            }
        }

        // HIGH
        List<Long> highIds = reviewsRepository.findRandomHighAppIds(excludeSince, highThreshold, PageRequest.of(0, 5));
        Optional<Long> highOpt = highIds.stream().filter(id -> !chosenIds.contains(id)).findFirst();
        if (highOpt.isEmpty()) {
            List<Long> relaxed = reviewsRepository.findRandomHighAppIds(includeAll, highThreshold, PageRequest.of(0, 5));
            highOpt = relaxed.stream().filter(id -> !chosenIds.contains(id)).findFirst();
        }
        highOpt.ifPresent(id -> {
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, Category.HIGH.name(), OffsetDateTime.now()));
        });

        // fill remaining ANY
        while (picks.size() < 5) {
            List<Long> anyIds = reviewsRepository.findRandomAnyAppIds(excludeSince, PageRequest.of(0, 5));
            Optional<Long> next = anyIds.stream().filter(id -> !chosenIds.contains(id)).findFirst();
            if (next.isEmpty()) {
                List<Long> relaxed = reviewsRepository.findRandomAnyAppIds(includeAll, PageRequest.of(0, 5));
                next = relaxed.stream().filter(id -> !chosenIds.contains(id)).findFirst();
            }
            if (next.isEmpty()) break;
            Long id = next.get();
            chosenIds.add(id);
            picks.add(new ReviewGamePick(null, today, id, Category.ANY.name(), OffsetDateTime.now()));
        }

        final List<ReviewGamePick> saved = pickRepository.saveAll(picks);
        log.info("Generated {} review-game picks for {} (low<= {}, high>= {})", saved.size(), today, lowThreshold, highThreshold);
        return saved;
    }

    public enum Category {HIGH, LOW, ANY}
}


