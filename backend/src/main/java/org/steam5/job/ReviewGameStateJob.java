package org.steam5.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.ReviewGameStateService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@DisallowConcurrentExecution
public class ReviewGameStateJob implements Job {

    private final ReviewGameStateService service;
    private final MeterRegistry meterRegistry;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0L);
    private final AtomicLong lastSuccessSize = new AtomicLong(0L);

    public ReviewGameStateJob(ReviewGameStateService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        // Source of truth for the `Steam5DailyPicksMissing` alert. The gauge
        // value is updated imperatively after every successful run; before
        // the first successful generation it stays at 0, which makes the
        // alert fire — that's intentional (an unbootstrapped service can't
        // serve a game today either).
        Gauge.builder("steam5.daily.picks.last.success.epoch.seconds", lastSuccessEpochSeconds, AtomicLong::doubleValue)
                .description("Unix timestamp of the last successful daily-picks generation")
                .register(meterRegistry);
        Gauge.builder("steam5.daily.picks.last.success.size", lastSuccessSize, AtomicLong::doubleValue)
                .description("Number of picks produced by the last successful daily-picks generation")
                .register(meterRegistry);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        log.info("Job start ReviewGameStateJob key={} fireTime={} scheduled={} refireCount={}",
                context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());
        try {
            final var picks = service.generateDailyPicks();
            lastSuccessEpochSeconds.set(System.currentTimeMillis() / 1000L);
            lastSuccessSize.set(picks == null ? 0L : picks.size());
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("outcome", "success")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.error("ReviewGameState generation failed", e);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("outcome", "failure")
                    .register(meterRegistry)
                    .increment();
            throw new JobExecutionException(e, false);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Job end ReviewGameStateJob durationMs={} nextFireTime={}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
        }
    }

    @Bean("ReviewGameStateJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(ReviewGameStateJob.class)
                .storeDurably()
                .withIdentity("ReviewGameStateJob")
                .build();
    }
}
