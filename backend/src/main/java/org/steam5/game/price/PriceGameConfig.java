package org.steam5.game.price;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "games.price")
public class PriceGameConfig {

    private int doNotRepeatDays = 365;
    private List<Integer> bucketBoundaries = List.of(499, 1499, 2999);
    private List<String> bucketTitles = List.of();
    private int roundsPerDay = 1;
    private String currency = "USD";
}
