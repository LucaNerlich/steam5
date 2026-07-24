package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.domain.LeaderboardType;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;

import java.util.concurrent.TimeUnit;

/**
 * Refreshes one leaderboard materialized view per firing, driven by the "type" JobDataMap
 * entry set on each of the four JobDetail beans below. A single parameterized job class
 * (rather than four near-identical ones) keeps the refresh-then-evict flow in one place.
 */
@Component
@Slf4j
@DisallowConcurrentExecution
public class LeaderboardRefreshJob implements Job {

    private final LeaderboardRefreshService refreshService;
    private final DomainCacheEvictor cacheEvictor;

    public LeaderboardRefreshJob(LeaderboardRefreshService refreshService, DomainCacheEvictor cacheEvictor) {
        this.refreshService = refreshService;
        this.cacheEvictor = cacheEvictor;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        final JobDataMap map = context.getMergedJobDataMap();
        final LeaderboardType type = LeaderboardType.valueOf(String.valueOf(map.get("type")));
        Exception caughtException = null;
        log.info("LeaderboardRefreshJob[{}] starting", type);
        try {
            switch (type) {
                case ALL_TIME -> refreshService.refreshAllTime();
                case MONTHLY -> refreshService.refreshMonthly();
                case WEEKLY -> refreshService.refreshWeekly();
                case SEASON -> refreshService.refreshSeason();
            }
        } catch (Exception e) {
            log.error("LeaderboardRefreshJob[{}] failed", type, e);
            caughtException = e;
        } finally {
            cacheEvictor.evictLeaderboardStatic();
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("LeaderboardRefreshJob[{}] completed in {}ms", type, ms);
            if (caughtException != null) {
                throw new JobExecutionException(caughtException, false);
            }
        }
    }

    @Bean("LeaderboardRefreshJob_AllTime")
    public JobDetail allTimeJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_AllTime")
                .usingJobData("type", LeaderboardType.ALL_TIME.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_Monthly")
    public JobDetail monthlyJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Monthly")
                .usingJobData("type", LeaderboardType.MONTHLY.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_Weekly")
    public JobDetail weeklyJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Weekly")
                .usingJobData("type", LeaderboardType.WEEKLY.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_Season")
    public JobDetail seasonJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Season")
                .usingJobData("type", LeaderboardType.SEASON.name())
                .build();
    }
}
