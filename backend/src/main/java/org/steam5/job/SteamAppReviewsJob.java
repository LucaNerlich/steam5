package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppReviewsFetcher;

import java.util.concurrent.TimeUnit;

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
        final long start = System.nanoTime();
        log.info("Job start SteamAppReviewsJob key={} fireTime={} scheduled={} refireCount={}",
                context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());
        try {
            fetcher.ingest();
        } catch (Exception e) {
            // bail out on any error (including rate limiting 429)
            log.error("SteamAppReviews ingestion failed - aborting job", e);
            throw new JobExecutionException(e, false);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Job end SteamAppReviewsJob durationMs={} nextFireTime={}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
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


