package org.steam5.game.price;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "games.price")
public class PriceGameConfig {

    private int doNotRepeatDays = 365;
    private List<Integer> bucketBoundaries = List.of(499, 1499, 2999);
    private List<String> bucketTitles = List.of();
    private int roundsPerDay = 3;
    private String currency = "USD";
}
