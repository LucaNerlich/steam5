package org.steam5.game.year;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.http.SteamApiException;
import org.steam5.job.blurhash.BlurhashEnqueueListener;
import org.steam5.job.events.BlurhashEncodeRequested;
import org.steam5.repository.ExcludedAppRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.service.SteamAppDetailsFetcher;
import org.steam5.util.ReleaseDateParser;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class YearPickGenerator {

    private static final int MIN_RECENT_RELEASE_DAYS = 7;

    private final SteamAppDetailRepository detailRepository;
    private final SteamAppDetailsFetcher detailsFetcher;
    private final ExcludedAppRepository excludedAppRepository;
    private final YearGameConfig config;
    private final ApplicationEventPublisher eventPublisher;

    public List<YearGamePick> createPicks(final LocalDate today) {
        final int doNotRepeatDays = Math.max(0, config.getDoNotRepeatDays());
        final LocalDate excludeSince = doNotRepeatDays >= 36500
                ? LocalDate.of(1970, 1, 1)
                : today.minusDays(doNotRepeatDays);

        final int rounds = Math.max(1, config.getRoundsPerDay());
        final List<YearGamePick> picks = new ArrayList<>(rounds);
        final Set<Long> chosenIds = new HashSet<>();

        for (int round = 1; round <= rounds; round++) {
            boolean added = false;
            final List<Long> candidates = detailRepository.findRandomAnyReleaseYear(
                    excludeSince, PageRequest.of(0, 12));

            for (Long id : candidates) {
                if (!chosenIds.contains(id) && validateAppOrExclude(id)) {
                    chosenIds.add(id);
                    picks.add(new YearGamePick(null, today, id, OffsetDateTime.now()));
                    log.info("Year round {}: picked appId {}", round, id);
                    added = true;
                    break;
                }
            }

            if (!added) {
                log.warn("Year round {}: no eligible app found", round);
            }
        }

        Collections.shuffle(picks);
        return picks;
    }

    public void enrichPickedApp(final YearGamePick pick) {
        try {
            detailsFetcher.fetchForAppId(pick.getAppId());
        } catch (Exception e) {
            log.warn("Failed to refresh details for picked year-game appId {}", pick.getAppId(), e);
        }

        eventPublisher.publishEvent(new BlurhashEncodeRequested(pick.getAppId(), null, BlurhashEnqueueListener.Type.SCREENSHOT));
    }

    private boolean validateAppOrExclude(final Long appId) {
        try {
            final boolean fetched = detailsFetcher.fetchForAppId(appId);
            if (!fetched) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game details fetch failed or success=false", OffsetDateTime.now()));
                return false;
            }

            final Optional<SteamAppDetail> detail = detailRepository.findByAppId(appId);
            if (detail.isEmpty()) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game missing details after fetch", OffsetDateTime.now()));
                return false;
            }

            final String releaseDate = detail.get().getReleaseDate();
            if (ReleaseDateParser.isComingSoonOrUnknown(releaseDate)
                    || ReleaseDateParser.parseYear(releaseDate).isEmpty()) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game unparseable release date: " + releaseDate, OffsetDateTime.now()));
                return false;
            }

            if (ReleaseDateParser.isReleasedWithinDays(releaseDate, MIN_RECENT_RELEASE_DAYS)) {
                excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                        "year-game released within last " + MIN_RECENT_RELEASE_DAYS + " days", OffsetDateTime.now()));
                return false;
            }

            return true;
        } catch (SteamApiException sae) {
            if (sae.getStatusCode() == 429) {
                log.warn("Steam API rate limited (429) while validating year-game appId {}. Aborting pick generation.", appId);
                throw new RuntimeException(sae);
            }
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "year-game details fetch error: HTTP " + sae.getStatusCode(), OffsetDateTime.now()));
            return false;
        } catch (Exception e) {
            excludedAppRepository.save(new org.steam5.domain.ExcludedApp(appId,
                    "year-game details fetch error: " + e.getMessage(), OffsetDateTime.now()));
            return false;
        }
    }
}
