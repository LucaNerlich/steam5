package org.steam5.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Date;
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
        // 00:40 UTC, after the seasons finalizer settles season boundaries.
        return nightly("LeaderboardRefreshJob_AllTime_Nightly_Trigger", job, "0 40 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-all-time", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshAllTimeIntraday(@Qualifier("LeaderboardRefreshJob_AllTime") JobDetail job) {
        return intraday("LeaderboardRefreshJob_AllTime_Intraday_Trigger", job);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyNightly(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return nightly("LeaderboardRefreshJob_Monthly_Nightly_Trigger", job, "0 42 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyIntraday(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return intraday("LeaderboardRefreshJob_Monthly_Intraday_Trigger", job);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyNightly(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return nightly("LeaderboardRefreshJob_Weekly_Nightly_Trigger", job, "0 44 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyIntraday(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return intraday("LeaderboardRefreshJob_Weekly_Intraday_Trigger", job);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-season", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshSeasonNightly(@Qualifier("LeaderboardRefreshJob_Season") JobDetail job) {
        return nightly("LeaderboardRefreshJob_Season_Nightly_Trigger", job, "0 46 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-season", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshSeasonIntraday(@Qualifier("LeaderboardRefreshJob_Season") JobDetail job) {
        return intraday("LeaderboardRefreshJob_Season_Intraday_Trigger", job);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-hardest-games", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshHardestGamesNightly(@Qualifier("LeaderboardRefreshJob_HardestGames") JobDetail job) {
        return nightly("LeaderboardRefreshJob_HardestGames_Nightly_Trigger", job, "0 48 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-hardest-games", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshHardestGamesIntraday(@Qualifier("LeaderboardRefreshJob_HardestGames") JobDetail job) {
        return intraday("LeaderboardRefreshJob_HardestGames_Intraday_Trigger", job);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-perfect-days", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshPerfectDaysNightly(@Qualifier("LeaderboardRefreshJob_PerfectDays") JobDetail job) {
        return nightly("LeaderboardRefreshJob_PerfectDays_Nightly_Trigger", job, "0 50 0 * * ?");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-perfect-days", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshPerfectDaysIntraday(@Qualifier("LeaderboardRefreshJob_PerfectDays") JobDetail job) {
        return intraday("LeaderboardRefreshJob_PerfectDays_Intraday_Trigger", job);
    }

    private static Trigger nightly(final String identity, final JobDetail job, final String cron) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity(identity)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(TimeZone.getTimeZone("UTC")))
                .build();
    }

    /**
     * Delayed start (not startNow()): a trigger that fires at scheduler startup can run before
     * LeaderboardMvBootstrapConfig has created the views, or collide with an old process's
     * still-finishing REFRESH during a redeploy. Every 10 minutes matches the leaderboard-static TTL.
     */
    private static Trigger intraday(final String identity, final JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity(identity)
                .startAt(Date.from(Instant.now().plusSeconds(60)))
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }
}
