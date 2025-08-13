package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.ReviewGameStateService;

import java.util.concurrent.TimeUnit;

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
        final long start = System.nanoTime();
        log.info("Job start ReviewGameStateJob key={} fireTime={} scheduled={} refireCount={}",
                context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());
        try {
            service.generateDailyPicks();
        } catch (Exception e) {
            log.error("ReviewGameState generation failed", e);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Job end ReviewGameStateJob durationMs={} nextFireTime={}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
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


