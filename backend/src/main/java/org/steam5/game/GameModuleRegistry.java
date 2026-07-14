package org.steam5.game;

import org.steam5.game.price.PriceGameModule;
import org.steam5.game.review.ReviewGameModule;
import org.steam5.game.year.YearGameModule;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Component
public class GameModuleRegistry {

    private final Map<GameId, DailyGameModule<?>> modulesById = new EnumMap<>(GameId.class);

    public GameModuleRegistry(final ReviewGameModule reviewGameModule,
                              final YearGameModule yearGameModule,
                              final PriceGameModule priceGameModule) {
        register(reviewGameModule);
        register(yearGameModule);
        register(priceGameModule);
    }

    private void register(final DailyGameModule<?> module) {
        modulesById.put(module.gameId(), module);
    }

    public Optional<DailyGameModule<?>> find(final GameId gameId) {
        return Optional.ofNullable(modulesById.get(gameId));
    }

    @SuppressWarnings("unchecked")
    public <P> Optional<DailyGameModule<P>> findTyped(final GameId gameId) {
        return Optional.ofNullable((DailyGameModule<P>) modulesById.get(gameId));
    }
}
