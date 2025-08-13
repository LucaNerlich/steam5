package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppDetailsFetcher;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
        final long start = System.nanoTime();
        log.info("Job start SteamAppDetailJob key={} fireTime={} scheduled={} refireCount={}",
                context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());
        try {
            fetcher.ingest();
        } catch (IOException e) {
            log.error("SteamAppDetail ingestion failed", e);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Job end SteamAppDetailJob durationMs={} nextFireTime={}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
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
