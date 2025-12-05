package org.steam5.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.steam5.domain.SeasonAwardCategory;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "season")
public class SeasonProperties {

    /**
     * Default number of days a season should run before closure.
     * Persisted seasons keep their own start/end dates even if this value changes later.
     */
    private int lengthDays = 30;

    private Awards awards = new Awards();

    @Getter
    @Setter
    public static class Awards {

        /**
         * Maximum number of placement levels to award per category (1 = winner only).
         */
        private int maxPlacements = 1;

        /**
         * Categories evaluated for each season.
         */
        private List<SeasonAwardCategory> categories = List.of(
                SeasonAwardCategory.MOST_POINTS,
                SeasonAwardCategory.MOST_HITS,
                SeasonAwardCategory.HIGHEST_AVG_POINTS_PER_DAY,
                SeasonAwardCategory.LONGEST_STREAK
        );
    }
}


