package org.steam5.game.price;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PricePickGenerator {

    private final PriceGameConfig config;

    public List<PriceGamePick> createPicks(final LocalDate today) {
        throw new UnsupportedOperationException("Price guesser pick generation is not implemented yet");
    }
}
