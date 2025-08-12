package org.steam5.config;

import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public Trigger triggerSteamAppDetailFetchJob(@Qualifier("SteamAppDetailFetchJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppDetailFetchJob_Trigger")
                //.withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds(15))
                .build();
    }

    @Bean
    public Trigger triggerSteamAppListDownloadJob(@Qualifier("SteamAppListDownloadJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppListDownloadJob_Trigger")
                //.withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds(15))
                .build();
    }

    @Bean
    public Trigger triggerSSteamAppReviewsJob(@Qualifier("SteamAppReviewsJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppReviewsJob_Trigger")
                //.withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds(15))
                .build();
    }

}
