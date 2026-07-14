package org.steam5.game;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GameModuleRegistry {

    private final Map<GameId, DailyGameModule<?>> modulesById = new EnumMap<>(GameId.class);

    public GameModuleRegistry(final List<DailyGameModule<?>> modules) {
        modules.forEach(this::register);
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
