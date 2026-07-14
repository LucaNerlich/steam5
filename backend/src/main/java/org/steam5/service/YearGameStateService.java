package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.year.YearGameConfig;
import org.steam5.game.year.YearGameModule;
import org.steam5.game.year.YearGamePick;
import org.steam5.game.year.YearHintBuilder;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.util.ReleaseDateParser;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YearGameStateService {

    private final DailyGameStateService dailyGameStateService;
    private final YearGameModule yearGameModule;
    private final YearGameConfig config;
    private final SteamAppDetailRepository detailRepository;

    public List<YearGamePick> generateDailyPicks() {
        return dailyGameStateService.generateDailyPicks(yearGameModule);
    }

    @Cacheable(value = "year-game", key = "#appId + ':release-year'")
    public int getReleaseYearForApp(final Long appId) {
        return detailRepository.findByAppId(appId)
                .flatMap(detail -> ReleaseDateParser.parseYear(detail.getReleaseDate()))
                .orElse(0);
    }

    public String getReleaseDateForApp(final Long appId) {
        return detailRepository.findByAppId(appId)
                .map(SteamAppDetail::getReleaseDate)
                .orElse(null);
    }

    public YearGameConfig getConfig() {
        return config;
    }

    public List<HintTierMeta> getHintTiers() {
        final ArrayList<HintTierMeta> tiers = new ArrayList<>(4);
        tiers.add(new HintTierMeta(0, "No hints", "Guess the release year from store details alone.", config.getMaxPoints()));
        tiers.add(new HintTierMeta(1, "Era", "Broad decade the game likely belongs to.", config.getMaxPoints() - 1));
        tiers.add(new HintTierMeta(2, "Narrow range", "A tighter year window around the answer.", config.getMaxPoints() - 2));
        tiers.add(new HintTierMeta(3, "Store date", "The release date as Steam lists it.", config.getMaxPoints() - 3));
        return tiers;
    }

    public String buildHintContent(final int hintLevel, final Long appId) {
        final int actualYear = getReleaseYearForApp(appId);
        return switch (hintLevel) {
            case 1 -> YearHintBuilder.buildEraHint(actualYear);
            case 2 -> YearHintBuilder.buildNarrowRangeHint(actualYear, config.getNarrowRangeWindowYears());
            case 3 -> YearHintBuilder.buildStoreDateHint(getReleaseDateForApp(appId));
            default -> throw new IllegalArgumentException("Invalid hint level: " + hintLevel);
        };
    }

    public List<SteamAppDetail> sanitizeForGameplay(final List<SteamAppDetail> details) {
        return details.stream().map(this::sanitizeForGameplay).toList();
    }

    public SteamAppDetail sanitizeForGameplay(final SteamAppDetail detail) {
        if (detail == null) {
            return null;
        }
        detail.setReleaseDate(null);
        return detail;
    }

    public record HintTierMeta(int level, String label, String description, int maxPoints) {
    }
}
