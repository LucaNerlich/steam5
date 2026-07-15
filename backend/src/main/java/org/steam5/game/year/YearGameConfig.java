package org.steam5.game.year;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "games.year")
public class YearGameConfig {

    private int doNotRepeatDays = 365;
    private int roundsPerDay = 1;
    private int maxPoints = 5;
    /**
     * Wrong-guess distance thresholds to unlock hints 1, 2, and 3 respectively.
     */
    private List<Integer> hintDistanceThresholds = List.of(12, 6, 2);
    /** Half-width in years for the narrow-range hint (actual ± window). */
    private int narrowRangeWindowYears = 2;
}
