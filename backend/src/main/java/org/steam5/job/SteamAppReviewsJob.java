package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppReviewsFetcher;

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
        fetcher.ingest();
        log.info("SteamAppReviews ingestion ended");
    }

    @Bean("SteamAppReviewsJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppReviewsJob.class)
                .storeDurably()
                .withIdentity("SteamAppReviewsJob")
                .build();
    }
}


