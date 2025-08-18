package org.steam5.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                // every day at 00:01am
                .withSchedule(CronScheduleBuilder.cronSchedule("0 1 0 * * ?"))
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
}
