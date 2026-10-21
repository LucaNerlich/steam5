package org.steam5.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.service.ReviewGameStateService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@DisallowConcurrentExecution
public class ReviewGameStateJob implements Job {

    private final ReviewGameStateService service;
    private final ReviewGamePickRepository pickRepository;
    private final MeterRegistry meterRegistry;
    // Quartz hands each firing a fresh job bean, so these must be static for the
    // gauges registered from the startup singleton to observe execute()'s writes.
    private static final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0L);
    private static final AtomicLong lastSuccessSize = new AtomicLong(0L);

    public ReviewGameStateJob(ReviewGameStateService service,
                              ReviewGamePickRepository pickRepository,
                              MeterRegistry meterRegistry) {
        this.service = service;
        this.pickRepository = pickRepository;
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

        // A restart starts both counters at 0, so the missing-picks alert fires even when
        // today's picks already exist. Seed from the newest generated day; execute() keeps
        // them current afterwards. Every per-fire bean also runs this, so skip once seeded.
        if (lastSuccessEpochSeconds.get() == 0L) {
            pickRepository.findTopByOrderByCreatedAtDesc().ifPresent(latest -> {
                if (latest.getCreatedAt() != null) {
                    lastSuccessEpochSeconds.set(latest.getCreatedAt().toEpochSecond());
                    lastSuccessSize.set(pickRepository.findByPickDate(latest.getPickDate()).size());
                }
            });
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            final var picks = service.generateDailyPicks();
            if (picks != null && !picks.isEmpty()) {
                lastSuccessEpochSeconds.set(System.currentTimeMillis() / 1000L);
                lastSuccessSize.set(picks.size());
                Counter.builder("steam5.daily.picks.generated")
                        .description("Daily-picks generation runs by outcome")
                        .tag("outcome", "success")
                        .register(meterRegistry)
                        .increment();
            } else {
                // An empty result means generation produced no picks (e.g. Steam
                // outage). Recording it as success would silence the
                // Steam5DailyPicksMissing alert for a day without a game.
                log.warn("ReviewGameState generation produced no picks; recording failure outcome");
                Counter.builder("steam5.daily.picks.generated")
                        .description("Daily-picks generation runs by outcome")
                        .tag("outcome", "failure")
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.error("ReviewGameState generation failed", e);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("outcome", "failure")
                    .register(meterRegistry)
                    .increment();
            throw new JobExecutionException(e, false);
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
