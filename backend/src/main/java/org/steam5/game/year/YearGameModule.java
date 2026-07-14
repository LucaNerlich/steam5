package org.steam5.game.year;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.steam5.game.DailyGameModule;
import org.steam5.game.GameId;
import org.steam5.service.DomainCacheEvictor;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YearGameModule implements DailyGameModule<YearGamePick> {

    private final YearGamePickRepository pickRepository;
    private final YearGamePickLockRepository pickLockRepository;
    private final YearPickGenerator pickGenerator;
    private final DomainCacheEvictor cacheEvictor;

    @Override
    public GameId gameId() {
        return GameId.RELEASE_YEAR_GUESSER;
    }

    @Override
    public List<YearGamePick> findPicksForDate(final LocalDate date) {
        return pickRepository.findByPickDate(date);
    }

    @Override
    public int tryAcquireLock(final LocalDate date) {
        return pickLockRepository.tryAcquire(date);
    }

    @Override
    public List<YearGamePick> createPicks(final LocalDate date) {
        return pickGenerator.createPicks(date);
    }

    @Override
    public List<YearGamePick> savePicks(final List<YearGamePick> picks) {
        return pickRepository.saveAll(picks);
    }

    @Override
    public void afterPicksCreated(final List<YearGamePick> picks) {
        // enrichment hooks added when year game ships
    }

    @Override
    public void evictCache() {
        cacheEvictor.evictGameState(GameId.RELEASE_YEAR_GUESSER);
    }

    @Override
    public String logLabel() {
        return "year-game";
    }
}
