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
public class SteamAppListJob implements Job {

    private final SteamAppListFetcher fetcher;

    public SteamAppListJob(SteamAppListFetcher fetcher) {
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
            log.info("SteamAppList ingestion ended");
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


