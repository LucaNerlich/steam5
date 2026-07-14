package org.steam5.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.steam5.domain.GameDate;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class DailyGameStateService {

    /**
     * Deliberately not {@code @Transactional}: the lock-poll loop and {@link DailyGameModule#createPicks}
     * make external HTTP calls, which must not run while holding a DB transaction/connection open.
     * Lock acquisition and persistence stay transactional via each module's repository calls.
     */
    public <P> List<P> generateDailyPicks(final DailyGameModule<P> module) {
        final LocalDate today = GameDate.todayUtc();
        final List<P> existing = module.findPicksForDate(today);
        if (!existing.isEmpty()) {
            return existing;
        }

        final int acquired = module.tryAcquireLock(today);
        if (acquired == 0) {
            for (int i = 0; i < 20; i++) {
                final List<P> concurrent = module.findPicksForDate(today);
                if (!concurrent.isEmpty()) {
                    return concurrent;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return module.findPicksForDate(today);
        }

        log.info("Generating {} picks for {}", module.logLabel(), today);

        final List<P> picks = module.createPicks(today);
        final List<P> saved = module.savePicks(picks);
        log.info("Generated {} {} picks for {}", saved.size(), module.logLabel(), today);

        if (!saved.isEmpty()) {
            module.afterPicksCreated(saved);
            evictCacheAfterCommit(module);
        }
        return saved;
    }

    private static void evictCacheAfterCommit(final DailyGameModule<?> module) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    module.evictCache();
                }
            });
        } else {
            module.evictCache();
        }
    }
}
