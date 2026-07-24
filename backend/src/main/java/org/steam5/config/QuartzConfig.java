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
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 1 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
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
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 0 2 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.seasons-finalizer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSeasonFinalizerJob(@Qualifier("SeasonFinalizerJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SeasonFinalizerJob_Trigger")
                // daily at 00:25 UTC — staggered after backfill so season SQL does not pile up at 00:10
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 25 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.seasons-backfill", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Trigger triggerSeasonBackfillJob(@Qualifier("SeasonBackfillJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SeasonBackfillJob_Trigger")
                // daily at 00:20 UTC — after review-game picks (00:01), before finalizer/spotlight
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 20 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.player-spotlight", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerPlayerSpotlightJob(@Qualifier("PlayerSpotlightJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("PlayerSpotlightJob_Trigger")
                // daily at 00:35 UTC — after season jobs settle streak data; avoids midnight pile-up
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 35 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-all-time", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshAllTimeNightly(@Qualifier("LeaderboardRefreshJob_AllTime") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_AllTime_Nightly_Trigger")
                // daily at 00:40 UTC — after seasons-finalizer (00:25) settles season boundaries
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 40 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-all-time", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshAllTimeIntraday(@Qualifier("LeaderboardRefreshJob_AllTime") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_AllTime_Intraday_Trigger")
                // every 10 minutes, matching the leaderboard-static Caffeine TTL
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyNightly(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Monthly_Nightly_Trigger")
                // daily at 00:42 UTC
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 42 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyIntraday(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Monthly_Intraday_Trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyNightly(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Weekly_Nightly_Trigger")
                // daily at 00:44 UTC
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 44 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyIntraday(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Weekly_Intraday_Trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-season", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshSeason(@Qualifier("LeaderboardRefreshJob_Season") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Season_Trigger")
                // daily at 00:46 UTC only — season boundary correctness matters more than
                // intraday freshness, so no additional intraday trigger for this type
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 46 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }
}
