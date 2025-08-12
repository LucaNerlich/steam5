package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.ReviewGameStateService;

@Component
@Slf4j
@DisallowConcurrentExecution
public class ReviewGameStateJob implements Job {

    private final ReviewGameStateService service;

    public ReviewGameStateJob(ReviewGameStateService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("ReviewGameState generation started");
        try {
            service.generateDailyPicks();
        } catch (Exception e) {
            log.error("ReviewGameState generation failed", e);
        } finally {
            log.info("ReviewGameState generation ended");
        }
    }

    @Bean("ReviewGameStateJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(ReviewGameStateJob.class)
                .storeDurably()
                .withIdentity("ReviewGameStateJob")
                .build();
    }
}


