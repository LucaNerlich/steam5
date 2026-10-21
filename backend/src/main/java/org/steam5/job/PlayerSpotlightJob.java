package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.PlayerSpotlightService;

@Component
@Slf4j
@DisallowConcurrentExecution
public class PlayerSpotlightJob implements Job {

    private final PlayerSpotlightService playerSpotlightService;

    public PlayerSpotlightJob(PlayerSpotlightService playerSpotlightService) {
        this.playerSpotlightService = playerSpotlightService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            playerSpotlightService.computeAndPersistForToday();
        } catch (Exception ex) {
            log.error("PlayerSpotlight computation failed", ex);
            // Rethrow so Quartz records the failure instead of reporting success.
            throw new JobExecutionException(ex, false);
        }
    }

    @Bean("PlayerSpotlightJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(PlayerSpotlightJob.class)
                .storeDurably()
                .withIdentity("PlayerSpotlightJob")
                .build();
    }
}
