package org.steam5.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();

    private JobDetail jobWithKey(String name) {
        JobDetail job = mock(JobDetail.class);
        when(job.getKey()).thenReturn(new JobKey(name));
        return job;
    }

    @Test
    void triggerLeaderboardRefreshPerfectDaysNightly_targetsThePerfectDaysJob() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_PerfectDays");

        Trigger trigger = config.triggerLeaderboardRefreshPerfectDaysNightly(job);

        assertEquals("LeaderboardRefreshJob_PerfectDays_Nightly_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
    }

    @Test
    void triggerLeaderboardRefreshPerfectDaysNightly_isADailyCronScheduleAtZeroFiftyUtc() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_PerfectDays");

        Trigger trigger = config.triggerLeaderboardRefreshPerfectDaysNightly(job);

        // daily at 00:50 UTC
        CronTrigger cronTrigger = assertInstanceOf(CronTrigger.class, trigger);
        assertEquals("0 50 0 * * ?", cronTrigger.getCronExpression());
        assertEquals(TimeZone.getTimeZone("UTC"), cronTrigger.getTimeZone());
    }

    @Test
    void triggerLeaderboardRefreshPerfectDaysIntraday_targetsThePerfectDaysJob() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_PerfectDays");

        Trigger trigger = config.triggerLeaderboardRefreshPerfectDaysIntraday(job);

        assertEquals("LeaderboardRefreshJob_PerfectDays_Intraday_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
    }
}