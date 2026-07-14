package org.steam5.game;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-game contract for daily pick generation. Each game module owns its
 * persistence (pick table, lock table) and game-specific pick logic.
 */
public interface DailyGameModule<P> {

    GameId gameId();

    List<P> findPicksForDate(LocalDate date);

    int tryAcquireLock(LocalDate date);

    List<P> createPicks(LocalDate date);

    List<P> savePicks(List<P> picks);

    void afterPicksCreated(List<P> picks);

    void evictCache();

    String logLabel();
}
