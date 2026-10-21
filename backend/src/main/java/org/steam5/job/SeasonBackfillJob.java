package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SeasonService;

import java.util.List;
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
        try {
            List<?> created = seasonService.backfillHistoricalSeasons();
            if (created.isEmpty()) {
                log.info("SeasonBackfillJob found no missing seasons.");
            } else {
                log.info("SeasonBackfillJob created {} season(s).", created.size());
            }
        } catch (Exception ex) {
            log.error("Season backfill failed", ex);
            // Rethrow so Quartz records the failure instead of reporting success.
            throw new JobExecutionException(ex, false);
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


