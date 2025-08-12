package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppReviewsFetcher;

import java.io.IOException;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppReviewsJob implements Job {

    private final SteamAppReviewsFetcher fetcher;

    public SteamAppReviewsJob(SteamAppReviewsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("SteamAppReviews ingestion started");
        try {
            fetcher.ingest();
        } catch (IOException e) {
            log.error("SteamAppReviews ingestion failed", e);
        } finally {
            log.info("SteamAppReviews ingestion ended");
        }
    }

    @Bean("SteamAppReviewsJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppReviewsJob.class)
                .storeDurably()
                .withIdentity("SteamAppReviewsJob")
                .build();
    }
}


