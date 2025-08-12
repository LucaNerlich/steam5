package org.steam5.config;

import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Configuration
public class QuartzConfig {

    @Bean
    public Trigger triggerSteamAppDetailJob(@Qualifier("SteamAppDetailJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppDetailJob_Trigger")
                //.withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds(15))
                .build();
    }

    @Bean
    public Trigger triggerSteamAppListJob(@Qualifier("SteamAppListJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppListJob_Trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInHours(24))
                .build();
    }

    @Bean
    public Trigger triggerSteamAppReviewsJob(@Qualifier("SteamAppReviewsJob") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("SteamAppReviewsJob_Trigger")
                //.withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds(15))
                .build();
    }
}
