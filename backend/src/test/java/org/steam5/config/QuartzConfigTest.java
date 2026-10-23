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
import java.util.function.Function;

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

    // ── Every type ─────────────────────────────────────────────────────────

    @Test
    void allLeaderboardTypesKeepTheirNightlyCronAndIntradayShape() {
        assertNightly("AllTime", "0 40 0 * * ?", config::triggerLeaderboardRefreshAllTimeNightly);
        assertNightly("Monthly", "0 42 0 * * ?", config::triggerLeaderboardRefreshMonthlyNightly);
        assertNightly("Weekly", "0 44 0 * * ?", config::triggerLeaderboardRefreshWeeklyNightly);
        assertNightly("Season", "0 46 0 * * ?", config::triggerLeaderboardRefreshSeasonNightly);
        assertNightly("HardestGames", "0 48 0 * * ?", config::triggerLeaderboardRefreshHardestGamesNightly);
        assertNightly("PerfectDays", "0 50 0 * * ?", config::triggerLeaderboardRefreshPerfectDaysNightly);

        assertIntraday("AllTime", config::triggerLeaderboardRefreshAllTimeIntraday);
        assertIntraday("Monthly", config::triggerLeaderboardRefreshMonthlyIntraday);
        assertIntraday("Weekly", config::triggerLeaderboardRefreshWeeklyIntraday);
        assertIntraday("Season", config::triggerLeaderboardRefreshSeasonIntraday);
        assertIntraday("HardestGames", config::triggerLeaderboardRefreshHardestGamesIntraday);
        assertIntraday("PerfectDays", config::triggerLeaderboardRefreshPerfectDaysIntraday);
    }

    private static void assertNightly(String suffix, String cron, Function<JobDetail, Trigger> factory) {
        final JobDetail job = jobWithKey("LeaderboardRefreshJob_" + suffix);
        final Trigger trigger = factory.apply(job);

        assertEquals("LeaderboardRefreshJob_" + suffix + "_Nightly_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
        final CronTrigger cronTrigger = assertInstanceOf(CronTrigger.class, trigger);
        assertEquals(cron, cronTrigger.getCronExpression());
        assertEquals(TimeZone.getTimeZone("UTC"), cronTrigger.getTimeZone());
    }

    private static void assertIntraday(String suffix, Function<JobDetail, Trigger> factory) {
        final JobDetail job = jobWithKey("LeaderboardRefreshJob_" + suffix);
        final Instant before = Instant.now();
        final Trigger trigger = factory.apply(job);

        assertEquals("LeaderboardRefreshJob_" + suffix + "_Intraday_Trigger", trigger.getKey().getName());
        assertEquals(job.getKey(), trigger.getJobKey());
        final SimpleTrigger simpleTrigger = assertInstanceOf(SimpleTrigger.class, trigger);
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, simpleTrigger.getRepeatCount());
        assertEquals(10L * 60L * 1000L, simpleTrigger.getRepeatInterval());
        final long startDelay = Duration.between(before, trigger.getStartTime().toInstant()).toMillis();
        assertTrue(startDelay >= 50_000 && startDelay <= 90_000);
    }
}