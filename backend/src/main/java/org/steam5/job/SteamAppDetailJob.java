package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppDetailsFetcher;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppDetailJob implements Job {

    private final SteamAppDetailsFetcher fetcher;

    public SteamAppDetailJob(final SteamAppDetailsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            fetcher.ingest();
        } catch (Exception e) {
            // bail out on any error (including rate limiting 429)
            log.error("SteamAppDetail ingestion failed - aborting job", e);
            throw new JobExecutionException(e, false);
        }
    }

    @Bean("SteamAppDetailJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppDetailJob.class)
                .storeDurably()
                .withIdentity("SteamAppDetailJob")
                .build();
    }
}
