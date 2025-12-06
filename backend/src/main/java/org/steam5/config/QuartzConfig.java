package org.steam5.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Configuration
public class QuartzConfig {

    @Bean
    @ConditionalOnProperty(prefix = "jobs.steam-app-details", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerSteamAppDetailJob(@Qualifier("SteamAppDetailJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppDetailJob_Trigger")
                .startNow()
                .withSchedule(simpleSchedule().repeatForever().withIntervalInHours(48))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.steam-app-list", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSteamAppListJob(@Qualifier("SteamAppListJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppListJob_Trigger")
                .startNow()
                .withSchedule(simpleSchedule().repeatForever().withIntervalInHours(24))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.steam-app-reviews", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSteamAppReviewsJob(@Qualifier("SteamAppReviewsJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppReviewsJob_Trigger")
                .startNow()
                .withSchedule(simpleSchedule().repeatForever().withIntervalInHours(48))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.review-game-state", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerReviewGameStateJob(@Qualifier("ReviewGameStateJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("ReviewGameStateJob_Trigger")
                // every day at 00:01 UTC (which is 01:01 CET / 02:01 CEST in Germany)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 1 0 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("UTC")))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.blurhash", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerBlurhashScreenshotJob(@Qualifier("BlurhashScreenshotsJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("BlurhashScreenshotsJob_Trigger")
                .startNow()
                .withSchedule(simpleSchedule().repeatForever().withIntervalInHours(24))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.reviews-refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerReviewsRefreshJob(@Qualifier("SteamAppReviewsRefreshJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppReviewsRefreshJob_Trigger")
                // nightly at 02:00
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.seasons-finalizer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSeasonFinalizerJob(@Qualifier("SeasonFinalizerJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SeasonFinalizerJob_Trigger")
                // daily at 00:10 UTC to close finished seasons
                .withSchedule(CronScheduleBuilder.cronSchedule("0 10 0 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("UTC")))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.seasons-backfill", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSeasonBackfillJob(@Qualifier("SeasonBackfillJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SeasonBackfillJob_Trigger")
                // daily at 00:05 UTC, after the finalizer
                .withSchedule(CronScheduleBuilder.cronSchedule("0 5 0 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("UTC")))
                .build();
    }
}
