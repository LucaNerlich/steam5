package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.PlayerSpotlightService;

@Component
@Slf4j
@DisallowConcurrentExecution
public class PlayerSpotlightJob implements Job {

    private final PlayerSpotlightService playerSpotlightService;
    private final DomainCacheEvictor cacheEvictor;

    public PlayerSpotlightJob(PlayerSpotlightService playerSpotlightService,
                              DomainCacheEvictor cacheEvictor) {
        this.playerSpotlightService = playerSpotlightService;
        this.cacheEvictor = cacheEvictor;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            playerSpotlightService.computeAndPersistForToday();
            cacheEvictor.evictPlayerSpotlights();
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
