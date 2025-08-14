package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppListFetcher;

import java.util.concurrent.TimeUnit;

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
        final long start = System.nanoTime();
        log.info("Job start SteamAppListJob key={} fireTime={} scheduled={} refireCount={}",
                context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());
        try {
            fetcher.ingest();
        } catch (Exception e) {
            // bail out on any error (including rate limiting 429)
            log.error("SteamAppList ingestion failed - aborting job", e);
            throw new JobExecutionException(e, false);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Job end SteamAppListJob durationMs={} nextFireTime={}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
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


