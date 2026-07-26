package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();

    private static JobDetail jobWithKey(String name) {
        JobDetail job = mock(JobDetail.class);
        when(job.getKey()).thenReturn(new JobKey(name));
        return job;
    }

    // ── Hardest Games ──────────────────────────────────────────────────────

    @Test
    void triggerLeaderboardRefreshHardestGamesNightly_targetsTheHardestGamesJob() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_HardestGames");

        Trigger trigger = config.triggerLeaderboardRefreshHardestGamesNightly(job);

        assertEquals("LeaderboardRefreshJob_HardestGames_Nightly_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
    }

    @Test
    void triggerLeaderboardRefreshHardestGamesNightly_isADailyCronScheduleAtZeroFortyEightUtc() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_HardestGames");

        Trigger trigger = config.triggerLeaderboardRefreshHardestGamesNightly(job);

        CronTrigger cronTrigger = assertInstanceOf(CronTrigger.class, trigger);
        assertEquals("0 48 0 * * ?", cronTrigger.getCronExpression());
        assertEquals(TimeZone.getTimeZone("UTC"), cronTrigger.getTimeZone());
    }

    @Test
    void triggerLeaderboardRefreshHardestGamesIntraday_targetsTheHardestGamesJob() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_HardestGames");

        Trigger trigger = config.triggerLeaderboardRefreshHardestGamesIntraday(job);

        assertEquals("LeaderboardRefreshJob_HardestGames_Intraday_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
    }

    @Test
    void triggerLeaderboardRefreshHardestGamesIntraday_isASimpleTriggerAtTenMinuteIntervalForever() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_HardestGames");

        Instant before = Instant.now();
        Trigger trigger = config.triggerLeaderboardRefreshHardestGamesIntraday(job);

        SimpleTrigger simpleTrigger = assertInstanceOf(SimpleTrigger.class, trigger);
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, simpleTrigger.getRepeatCount());
        assertEquals(10L * 60L * 1000L, simpleTrigger.getRepeatInterval());

        long startDelay = Duration.between(before, trigger.getStartTime().toInstant()).toMillis();
        assertTrue(startDelay >= 50_000 && startDelay <= 90_000,
                "Expected start delay ~60s but was " + startDelay + "ms");
    }

    // ── Perfect Days ───────────────────────────────────────────────────────

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

    @Test
    void triggerLeaderboardRefreshPerfectDaysIntraday_isASimpleTriggerAtTenMinuteIntervalForever() {
        JobDetail job = jobWithKey("LeaderboardRefreshJob_PerfectDays");

        Instant before = Instant.now();
        Trigger trigger = config.triggerLeaderboardRefreshPerfectDaysIntraday(job);

        SimpleTrigger simpleTrigger = assertInstanceOf(SimpleTrigger.class, trigger);
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, simpleTrigger.getRepeatCount());
        assertEquals(10L * 60L * 1000L, simpleTrigger.getRepeatInterval());

        long startDelay = Duration.between(before, trigger.getStartTime().toInstant()).toMillis();
        assertTrue(startDelay >= 50_000 && startDelay <= 90_000,
                "Expected start delay ~60s but was " + startDelay + "ms");
    }
}