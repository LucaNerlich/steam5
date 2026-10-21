package org.steam5.job;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.steam5.domain.ReviewGamePick;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.service.ReviewGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
        final ReviewGamePickRepository pickRepository = mock(ReviewGamePickRepository.class);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        resetGauges();

        // Startup singleton registers the gauges, as @PostConstruct does in production.
        final ReviewGameStateJob singleton = new ReviewGameStateJob(service, pickRepository, registry);
        singleton.registerGauges();

        when(service.generateDailyPicks()).thenReturn(List.of(
                new ReviewGamePick(1L, LocalDate.now(), 10L, OffsetDateTime.now()),
                new ReviewGamePick(2L, LocalDate.now(), 20L, OffsetDateTime.now())));

        // A per-fire instance performs the work.
        final ReviewGameStateJob firing = new ReviewGameStateJob(service, pickRepository, registry);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        final JobDetail detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(JobKey.jobKey("ReviewGameStateJob"));
        when(context.getJobDetail()).thenReturn(detail);
        firing.execute(context);

        assertEquals(2.0, registry.get("steam5.daily.picks.last.success.size").gauge().value());
        assertTrue(registry.get("steam5.daily.picks.last.success.epoch.seconds").gauge().value() > 0);
    }

    @Test
    void startupSeedsTheGaugesFromTheNewestGeneratedDay() throws Exception {
        final ReviewGameStateService service = mock(ReviewGameStateService.class);
        final ReviewGamePickRepository pickRepository = mock(ReviewGamePickRepository.class);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        resetGauges();
        final OffsetDateTime created = OffsetDateTime.now().minusHours(2);
        final ReviewGamePick latest = new ReviewGamePick(1L, LocalDate.now(), 10L, created);
        when(pickRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(latest));
        when(pickRepository.findByPickDate(created.toLocalDate())).thenReturn(List.of(latest));

        new ReviewGameStateJob(service, pickRepository, registry).registerGauges();

        assertTrue(registry.get("steam5.daily.picks.last.success.epoch.seconds").gauge().value() > 0);
        assertEquals(1.0, registry.get("steam5.daily.picks.last.success.size").gauge().value());
    }

    private static void resetGauges() throws Exception {
        for (final String name : List.of("lastSuccessEpochSeconds", "lastSuccessSize")) {
            final java.lang.reflect.Field field = ReviewGameStateJob.class.getDeclaredField(name);
            field.setAccessible(true);
            ((AtomicLong) field.get(null)).set(0L);
        }
    }
}
