package org.steam5.job;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.steam5.domain.ReviewGamePick;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Quartz hands each firing a fresh job bean, so the gauges registered from the startup
 * singleton must observe the counters written by a different instance's execute().
 */
class ReviewGameStateJobTest {

    @Test
    void gaugesReadTheStateWrittenByAnotherInstance() throws Exception {
        final ReviewGameStateService service = mock(ReviewGameStateService.class);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // Startup singleton registers the gauges, as @PostConstruct does in production.
        final ReviewGameStateJob singleton = new ReviewGameStateJob(service, registry);
        singleton.registerGauges();

        when(service.generateDailyPicks()).thenReturn(List.of(
                new ReviewGamePick(1L, LocalDate.now(), 10L, OffsetDateTime.now()),
                new ReviewGamePick(2L, LocalDate.now(), 20L, OffsetDateTime.now())));

        // A per-fire instance performs the work.
        final ReviewGameStateJob firing = new ReviewGameStateJob(service, registry);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        final JobDetail detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(JobKey.jobKey("ReviewGameStateJob"));
        when(context.getJobDetail()).thenReturn(detail);
        firing.execute(context);

        assertEquals(2.0, registry.get("steam5.daily.picks.last.success.size").gauge().value());
        assertTrue(registry.get("steam5.daily.picks.last.success.epoch.seconds").gauge().value() > 0);
    }
}
