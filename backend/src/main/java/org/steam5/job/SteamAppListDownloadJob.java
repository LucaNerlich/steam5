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
        log.info("SteamAppList ingestion started");
        try {
            fetcher.ingest();
        } catch (IOException e) {
            log.error("SteamAppList ingestion failed", e);
        } finally {
            log.info("SteamAppReviews ingestion ended");
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


