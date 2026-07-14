package org.steam5.game.year;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "games.year")
public class YearGameConfig {

    private int doNotRepeatDays = 365;
    private List<Integer> bucketBoundaries = List.of(1999, 2009, 2019);
    private List<String> bucketTitles = List.of();
    private int roundsPerDay = 3;
}
