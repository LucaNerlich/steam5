package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppDetailsFetcher;

import java.io.IOException;

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
        try {
            fetcher.ingest();
            log.info("SteamAppDetail ingestion ended");
        } catch (IOException e) {
            log.error("SteamAppDetail ingestion failed", e);
        }
    }

    @Bean("SteamAppDetailFetchJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppDetailFetchJob.class)
                .storeDurably()
                .withIdentity("SteamAppDetailFetchJob")
                .build();
    }
}
