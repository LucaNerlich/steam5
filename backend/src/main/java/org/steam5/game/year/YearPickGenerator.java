package org.steam5.game.year;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YearPickGenerator {

    private final YearGameConfig config;

    public List<YearGamePick> createPicks(final LocalDate today) {
        throw new UnsupportedOperationException("Release year guesser pick generation is not implemented yet");
    }
}
