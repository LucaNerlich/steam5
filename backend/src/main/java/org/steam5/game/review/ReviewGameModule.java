package org.steam5.game.review;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.steam5.domain.ReviewGamePick;
import org.steam5.game.DailyGameModule;
import org.steam5.game.GameId;
import org.steam5.repository.DailyPickLockRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.service.DomainCacheEvictor;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReviewGameModule implements DailyGameModule<ReviewGamePick> {

    private final ReviewGamePickRepository pickRepository;
    private final DailyPickLockRepository pickLockRepository;
    private final ReviewPickGenerator pickGenerator;
    private final DomainCacheEvictor cacheEvictor;

    @Override
    public GameId gameId() {
        return GameId.REVIEW_GUESSER;
    }

    @Override
    public List<ReviewGamePick> findPicksForDate(final LocalDate date) {
        return pickRepository.findByPickDate(date);
    }

    @Override
    public int tryAcquireLock(final LocalDate date) {
        return pickLockRepository.tryAcquire(date);
    }

    @Override
    public List<ReviewGamePick> createPicks(final LocalDate date) {
        return pickGenerator.createPicks(date);
    }

    @Override
    public List<ReviewGamePick> savePicks(final List<ReviewGamePick> picks) {
        return pickRepository.saveAll(picks);
    }

    @Override
    public void afterPicksCreated(final List<ReviewGamePick> picks) {
        for (ReviewGamePick pick : picks) {
            pickGenerator.enrichPickedApp(pick);
        }
    }

    @Override
    public void evictCache() {
        cacheEvictor.evictGameState(GameId.REVIEW_GUESSER);
    }

    @Override
    public String logLabel() {
        return "review-game";
    }
}
