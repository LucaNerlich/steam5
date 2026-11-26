package org.steam5.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.ReviewGameStateService;

@Component
@Slf4j
@DisallowConcurrentExecution
public class ReviewGameStateJob implements Job {

    private final ReviewGameStateService service;
    private final MeterRegistry meterRegistry;

    public ReviewGameStateJob(ReviewGameStateService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            final var picks = service.generateDailyPicks();
            if (picks != null && !picks.isEmpty()) {
                // The service records the last-success gauges; the job only counts outcomes.
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
