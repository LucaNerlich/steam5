package org.steam5.game.price;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.steam5.game.DailyGameModule;
import org.steam5.game.GameId;
import org.steam5.service.DomainCacheEvictor;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PriceGameModule implements DailyGameModule<PriceGamePick> {

    private final PriceGamePickRepository pickRepository;
    private final PriceGamePickLockRepository pickLockRepository;
    private final PricePickGenerator pickGenerator;
    private final DomainCacheEvictor cacheEvictor;

    @Override
    public GameId gameId() {
        return GameId.PRICE_GUESSER;
    }

    @Override
    public List<PriceGamePick> findPicksForDate(final LocalDate date) {
        return pickRepository.findByPickDate(date);
    }

    @Override
    public int tryAcquireLock(final LocalDate date) {
        return pickLockRepository.tryAcquire(date);
    }

    @Override
    public List<PriceGamePick> createPicks(final LocalDate date) {
        return pickGenerator.createPicks(date);
    }

    @Override
    public List<PriceGamePick> savePicks(final List<PriceGamePick> picks) {
        return pickRepository.saveAll(picks);
    }

    @Override
    public void afterPicksCreated(final List<PriceGamePick> picks) {
        // enrichment hooks added when price game ships
    }

    @Override
    public void evictCache() {
        cacheEvictor.evictGameState(GameId.PRICE_GUESSER);
    }

    @Override
    public String logLabel() {
        return "price-game";
    }
}
