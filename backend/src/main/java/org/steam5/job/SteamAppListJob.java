package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppListFetcher;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppListJob implements Job {

    private final SteamAppListFetcher fetcher;

    public SteamAppListJob(SteamAppListFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            fetcher.ingest();
        } catch (Exception e) {
            // bail out on any error (including rate limiting 429)
            log.error("SteamAppList ingestion failed - aborting job", e);
            throw new JobExecutionException(e, false);
        }
    }

    @Bean("SteamAppListJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppListJob.class)
                .storeDurably()
                .withIdentity("SteamAppListJob")
                .build();
    }
}


