package org.steam5.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.game.DailyGameStateService;
import org.steam5.game.year.YearGameModule;

@Component
@Slf4j
@DisallowConcurrentExecution
public class YearGameStateJob implements Job {

    private final DailyGameStateService dailyGameStateService;
    private final YearGameModule yearGameModule;
    private final MeterRegistry meterRegistry;

    public YearGameStateJob(final DailyGameStateService dailyGameStateService,
                            final YearGameModule yearGameModule,
                            final MeterRegistry meterRegistry) {
        this.dailyGameStateService = dailyGameStateService;
        this.yearGameModule = yearGameModule;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("Job start YearGameStateJob key={}", context.getJobDetail().getKey());
        try {
            dailyGameStateService.generateDailyPicks(yearGameModule);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("game", "year")
                    .tag("outcome", "success")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.error("YearGameState generation failed", e);
            Counter.builder("steam5.daily.picks.generated")
                    .description("Daily-picks generation runs by outcome")
                    .tag("game", "year")
                    .tag("outcome", "failure")
                    .register(meterRegistry)
                    .increment();
            throw new JobExecutionException(e, false);
        }
    }

    @Bean("YearGameStateJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(YearGameStateJob.class)
                .storeDurably()
                .withIdentity("YearGameStateJob")
                .build();
    }
}
