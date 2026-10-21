package org.steam5.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Wires Micrometer metrics for Quartz job executions.
 *
 * <p>Spring Boot 4.0.5 ships {@code QuartzEndpoint} (for {@code /actuator/quartz}) but does
 * <strong>not</strong> include any Micrometer binder/autoconfiguration that emits per-job
 * execution metrics. Micrometer 1.16.x core likewise has no {@code binder/quartz/} package.
 * Without explicit wiring no {@code quartz_*} series appear in {@code /actuator/prometheus}.
 *
 * <p>This config registers a global {@link JobListener} via
 * {@link SchedulerFactoryBeanCustomizer} that records a {@code quartz.jobs.execution} Timer
 * (rendered as {@code quartz_jobs_execution_seconds_*} by the Prometheus registry) tagged with
 * the job's group, name, and outcome (succeeded/failed/vetoed).
 */
@Slf4j
@Configuration
public class QuartzMetricsConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzMetricsCustomizer(final MeterRegistry meterRegistry) {
        return schedulerFactoryBean -> schedulerFactoryBean
                .setGlobalJobListeners(new MicrometerJobListener(meterRegistry));
    }

    @Slf4j
    static final class MicrometerJobListener implements JobListener {

        private static final String LISTENER_NAME = "micrometerQuartzJobListener";
        private static final String TIMER_NAME = "quartz.jobs.execution";
        private static final String DESCRIPTION = "Quartz job execution time";

        private final MeterRegistry meterRegistry;

        MicrometerJobListener(final MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public String getName() {
            return LISTENER_NAME;
        }

        @Override
        public void jobToBeExecuted(final JobExecutionContext context) {
            // One lifecycle log for every job: the classes no longer each copy-paste a start
            // line and a timing finally, and the shape is identical across jobs.
            log.info("Job start {} key={} fireTime={} scheduled={} refireCount={}",
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey(), context.getFireTime(),
                    context.getScheduledFireTime(), context.getRefireCount());
        }

        @Override
        public void jobExecutionVetoed(final JobExecutionContext context) {
            record(context, 0L, "vetoed");
        }

        @Override
        public void jobWasExecuted(final JobExecutionContext context, final JobExecutionException jobException) {
            final long runTimeMs = Math.max(0L, context.getJobRunTime());
            final String outcome = (jobException == null) ? "succeeded" : "failed";
            log.info("Job end {} durationMs={} outcome={} nextFireTime={}",
                    context.getJobDetail().getKey().getName(), runTimeMs, outcome,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
            record(context, runTimeMs, outcome);
        }

        private void record(final JobExecutionContext context, final long runTimeMs, final String outcome) {
            Timer.builder(TIMER_NAME)
                    .description(DESCRIPTION)
                    .tag("group", context.getJobDetail().getKey().getGroup())
                    .tag("name", context.getJobDetail().getKey().getName())
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(runTimeMs, TimeUnit.MILLISECONDS);
        }
    }
}
