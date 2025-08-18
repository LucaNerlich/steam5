package org.steam5.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "review.game")
public class ReviewGameConfig {

    /**
     * Number of days during which a previously picked appid should not be picked again.
     * If set very large, it approximates "never repeat". Default: 3650 (10 years).
     */
    private int doNotRepeatDays = 3650;

    /**
     * Upper bounds for review count buckets, in ascending order.
     * Example: [100, 1000, 10000] yields buckets: 1-100, 101-1000, 1001-10000, 10000+
     */
    private List<Integer> bucketBoundaries = List.of(100, 1000, 10000);

    /**
     * Percentile for low/high thresholds when partitioning by total reviews.
     * Must be in range [0.0, 1.0]. Defaults: low=0.25, high=0.90
     */
    private double lowPercentile = 0.25;
    private double highPercentile = 0.90;

    /**
     * If a picked app's reviews were updated before this many days ago, refresh them before using the pick.
     * Default: 7 days.
     */
    private int minReviewsFreshDays = 7;
}


