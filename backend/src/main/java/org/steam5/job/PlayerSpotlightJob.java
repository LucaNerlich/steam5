package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.PlayerSpotlightService;

import java.util.concurrent.TimeUnit;

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
        final long start = System.nanoTime();
        log.info("PlayerSpotlightJob fired at {}", context.getFireTime());
        try {
            playerSpotlightService.computeAndPersistForToday();
        } catch (Exception ex) {
            log.error("PlayerSpotlight computation failed", ex);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("PlayerSpotlightJob completed in {}ms; next fire {}",
                    durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
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
