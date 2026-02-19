package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.config.SteamAppsConfig;
import org.steam5.http.SteamApiException;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.SteamAppReviewsFetcher;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppReviewsRefreshJob implements Job {

    // Tuneables: number refreshed per run (respecting 1 req/sec via SteamHttpClient)
    private static final int DEFAULT_NIGHTLY_REFRESH = 2500; // fallback
    private final SteamAppReviewsFetcher fetcher;
    private final SteamAppReviewsRepository reviewsRepository;
    private final SteamAppsConfig steamAppsConfig;

    public SteamAppReviewsRefreshJob(SteamAppReviewsFetcher fetcher, SteamAppReviewsRepository reviewsRepository, SteamAppsConfig steamAppsConfig) {
        this.fetcher = fetcher;
        this.reviewsRepository = reviewsRepository;
        this.steamAppsConfig = steamAppsConfig;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        int refreshed = 0;
        try {
            int limit = steamAppsConfig.getReviewsNightlyLimit() > 0 ? steamAppsConfig.getReviewsNightlyLimit() : DEFAULT_NIGHTLY_REFRESH;
            final JobDataMap map = context.getMergedJobDataMap();
            if (map != null && map.containsKey("limit")) {
                try {
                    limit = Math.max(1, Integer.parseInt(String.valueOf(map.get("limit"))));
                } catch (Exception ignored) {
                }
            }
            final List<Long> ids = reviewsRepository.findIdsOrderByUpdatedAtAsc(org.springframework.data.domain.PageRequest.of(0, limit));
            for (Long appId : ids) {
                try {
                    fetcher.fetchForAppId(appId);
                    refreshed++;
                } catch (SteamApiException sae) {
                    // Respect rate limiting: abort job on 429
                    if (sae.getStatusCode() == 429) {
                        log.warn("Rate limited (429) while refreshing reviews for appId {} - aborting job", appId);
                        throw new JobExecutionException(sae, false);
                    }
                    // Log concise message without large response bodies
                    log.warn("Failed refreshing reviews for appId {}: HTTP {}", appId, sae.getStatusCode());
                } catch (Exception e) {
                    // Log with full exception for better debugging
                    log.warn("Failed refreshing reviews for appId {}", appId, e);
                }
            }
        } catch (Exception e) {
            log.error("SteamAppReviewsRefreshJob error", e);
            throw new JobExecutionException(e, false);
        } finally {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("SteamAppReviewsRefreshJob refreshed={} durationMs={}", refreshed, ms);
        }
    }

    @Bean("SteamAppReviewsRefreshJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(SteamAppReviewsRefreshJob.class)
                .storeDurably()
                .withIdentity("SteamAppReviewsRefreshJob")
                .build();
    }
}


