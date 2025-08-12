package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppListFetcher;

import java.io.IOException;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppListDownloadJob implements Job {

    private final SteamAppListFetcher fetcher;

    public SteamAppListDownloadJob(SteamAppListFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("SteamAppList ingestion started");
            fetcher.ingest();
            log.info("SteamAppList ingestion ended");
        } catch (IOException e) {
            log.error("SteamAppList ingestion failed", e);
        }
    }

    @Bean("SteamAppListDownloadJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppListDownloadJob.class)
                .storeDurably()
                .withIdentity("SteamAppListDownloadJob")
                .build();
    }
}


