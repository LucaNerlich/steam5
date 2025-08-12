package org.steam5.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "review.game")
public class ReviewGameConfig {

    /**
     * Number of days during which a previously picked appid should not be picked again.
     * If set very large, it approximates "never repeat". Default: 3650 (10 years).
     */
    private int doNotRepeatDays = 3650;
}


