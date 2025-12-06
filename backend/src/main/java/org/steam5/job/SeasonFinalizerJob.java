package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.domain.Season;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SeasonFinalizerJob implements Job {

    private final SeasonService seasonService;

    public SeasonFinalizerJob(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long started = System.nanoTime();
        final LocalDate todayUtc = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        log.info("SeasonFinalizerJob fired at {} (UTC date={})", context.getFireTime(), todayUtc);
        try {
            // Ensure there is a season covering today
            Season current = seasonService.ensureSeasonForDate(todayUtc);

            // Finalize any active seasons that already ended before today
            List<Season> activeSeasons = seasonService.findActiveSeasons();
            for (Season season : activeSeasons) {
                if (season.getEndDate().isBefore(todayUtc)) {
                    seasonService.finalizeSeason(season);
                }
            }

            // Re-check to make sure today is covered (in case multiple seasons were finalized)
            if (current.getEndDate().isBefore(todayUtc)) {
                seasonService.ensureSeasonForDate(todayUtc);
            }
        } catch (Exception ex) {
            log.error("Season finalization failed", ex);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("SeasonFinalizerJob completed in {}ms; next fire {}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
        }
    }

    @Bean("SeasonFinalizerJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SeasonFinalizerJob.class)
                .storeDurably()
                .withIdentity("SeasonFinalizerJob")
                .build();
    }
}


