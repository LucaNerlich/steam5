package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SeasonService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SeasonBackfillJob implements Job {

    private final SeasonService seasonService;

    public SeasonBackfillJob(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        log.info("SeasonBackfillJob fired at {}", context.getFireTime());
        try {
            List<?> created = seasonService.backfillHistoricalSeasons();
            if (created.isEmpty()) {
                log.info("SeasonBackfillJob found no missing seasons.");
            } else {
                log.info("SeasonBackfillJob created {} season(s).", created.size());
            }
        } catch (Exception ex) {
            log.error("Season backfill failed", ex);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("SeasonBackfillJob completed in {}ms; next fire {}",
                    durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
        }
    }

    @Bean("SeasonBackfillJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SeasonBackfillJob.class)
                .storeDurably()
                .withIdentity("SeasonBackfillJob")
                .build();
    }
}


