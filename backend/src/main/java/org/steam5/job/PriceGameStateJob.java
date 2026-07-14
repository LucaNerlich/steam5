package org.steam5.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.price.PriceGameModule;

@Component
@Slf4j
@DisallowConcurrentExecution
public class PriceGameStateJob implements Job {

    private final DailyGameStateService dailyGameStateService;
    private final PriceGameModule priceGameModule;
    private final MeterRegistry meterRegistry;

    public PriceGameStateJob(final DailyGameStateService dailyGameStateService,
                             final PriceGameModule priceGameModule,
                             final MeterRegistry meterRegistry) {
        this.dailyGameStateService = dailyGameStateService;
        this.priceGameModule = priceGameModule;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("Job start PriceGameStateJob key={}", context.getJobDetail().getKey());
        try {
            dailyGameStateService.generateDailyPicks(priceGameModule);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("game", "price")
                    .tag("outcome", "success")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.error("PriceGameState generation failed", e);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("game", "price")
                    .tag("outcome", "failure")
                    .register(meterRegistry)
                    .increment();
            throw new JobExecutionException(e, false);
        }
    }

    @Bean("PriceGameStateJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(PriceGameStateJob.class)
                .storeDurably()
                .withIdentity("PriceGameStateJob")
                .build();
    }
}
