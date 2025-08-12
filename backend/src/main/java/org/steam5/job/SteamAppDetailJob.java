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
public class SteamAppDetailJob implements Job {

    private final SteamAppDetailsFetcher fetcher;

    public SteamAppDetailJob(final SteamAppDetailsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("SteamAppDetail ingestion started");
        try {
            fetcher.ingest();
        } catch (IOException e) {
            log.error("SteamAppDetail ingestion failed", e);
        } finally {
            log.info("SteamAppDetail ingestion ended");
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
