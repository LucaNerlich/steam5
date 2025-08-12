package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppDetailsFetcher;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppDetailFetchJob implements Job {

    private final SteamAppDetailsFetcher fetcher;

    public SteamAppDetailFetchJob(final SteamAppDetailsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("SteamAppDetail ingestion started");
        fetcher.ingest();
        log.info("SteamAppDetail ingestion ended");
    }

    @Bean("SteamAppDetailFetchJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppDetailFetchJob.class)
                .storeDurably()
                .withIdentity("SteamAppDetailFetchJob")
                .build();
    }
}
