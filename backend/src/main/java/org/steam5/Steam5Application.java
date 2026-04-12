package org.steam5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
@EnableCaching
@EnableAsync   // Fix #11: enables @Async on SteamUserService.updateUserProfile()
@EnableConfigurationProperties
public class Steam5Application {

    public static void main(String[] args) {
        SpringApplication.run(Steam5Application.class, args);
    }

}
