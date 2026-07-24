package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.domain.Season;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SeasonFinalizerJob implements Job {

    private final SeasonService seasonService;
    private final LeaderboardRefreshService leaderboardRefreshService;
    private final DomainCacheEvictor cacheEvictor;

    public SeasonFinalizerJob(SeasonService seasonService, LeaderboardRefreshService leaderboardRefreshService, DomainCacheEvictor cacheEvictor) {
        this.seasonService = seasonService;
        this.leaderboardRefreshService = leaderboardRefreshService;
        this.cacheEvictor = cacheEvictor;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long started = System.nanoTime();
        final LocalDate todayUtc = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        log.info("SeasonFinalizerJob fired at {} (UTC date={})", context.getFireTime(), todayUtc);
        try {
            // Ensure there is a season covering today
            Season current = seasonService.ensureSeasonForDate(todayUtc);

            // Finalize any active seasons that already ended before today
            List<Season> activeSeasons = seasonService.findActiveSeasons();
            for (Season season : activeSeasons) {
                if (season.getEndDate().isBefore(todayUtc)) {
                    seasonService.finalizeSeason(season);
                }
            }
            // ensureSeasonForDate above guarantees current.endDate >= todayUtc,
            // so no further season creation is needed here.

            // Without this, mv_leaderboard_season would still reflect the previous season's
            // window until the next scheduled 00:46 UTC refresh — up to ~21 minutes during
            // which /season could serve the previous season's standings under the new
            // season's cache key (LeaderboardController#season keys its manual cache by
            // season number, which already flipped above).
            leaderboardRefreshService.refreshSeason();
        } catch (Exception ex) {
            log.error("Season finalization failed", ex);
        } finally {
            // Unconditional, matching LeaderboardRefreshJob's pattern: cheap and harmless even
            // if refreshSeason() above failed or wasn't reached, and prevents a request that
            // populated the leaderboard-static cache from an overnight-stale MV (e.g. just
            // before this job fired) from pairing stale entries with a fresher
            // X-Leaderboard-Refreshed-At header until the cache's own TTL clears it.
            cacheEvictor.evictLeaderboardStatic();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("SeasonFinalizerJob completed in {}ms; next fire {}", durationMs,
                    context.getTrigger() != null ? context.getTrigger().getNextFireTime() : null);
        }
    }

    @Bean("SeasonFinalizerJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SeasonFinalizerJob.class)
                .storeDurably()
                .withIdentity("SeasonFinalizerJob")
                .build();
    }
}


