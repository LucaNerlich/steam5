package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.http.SteamApiException;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.SteamAppReviewsFetcher;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@DisallowConcurrentExecution
public class SteamAppReviewsRefreshJob implements Job {

    private static final int DEFAULT_NIGHTLY_REFRESH = 2500;
    private static final int MAX_REFRESH_LIMIT = 10_000;

    private final SteamAppReviewsFetcher fetcher;
    private final SteamAppReviewsRepository reviewsRepository;
    private final CacheManager cacheManager;
    private final int configuredNightlyLimit;

    public SteamAppReviewsRefreshJob(SteamAppReviewsFetcher fetcher,
                                     SteamAppReviewsRepository reviewsRepository,
                                     CacheManager cacheManager,
                                     @Value("${jobs.reviews-refresh.nightly-limit:2500}") int configuredNightlyLimit) {
        this.fetcher = fetcher;
        this.reviewsRepository = reviewsRepository;
        this.cacheManager = cacheManager;
        this.configuredNightlyLimit = configuredNightlyLimit;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        int refreshed = 0;
        Exception caughtException = null;
        try {
            int limit = configuredNightlyLimit > 0 ? configuredNightlyLimit : DEFAULT_NIGHTLY_REFRESH;
            final JobDataMap map = context.getMergedJobDataMap();
            if (map != null && map.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(String.valueOf(map.get("limit")));
                } catch (Exception ignored) {
                }
            }
            // Clamp limit to [1, MAX_REFRESH_LIMIT] to prevent unbounded queries
            limit = Math.max(1, Math.min(limit, MAX_REFRESH_LIMIT));
            log.info("SteamAppReviewsRefreshJob starting with limit={}", limit);
            final List<Long> ids = reviewsRepository.findIdsOrderByUpdatedAtAsc(org.springframework.data.domain.PageRequest.of(0, limit));
            for (Long appId : ids) {
                try {
                    // Per-app cache eviction only; avoid clearing review-game aggregates on every
                    // app (that thrash forces DB reloads while this job is already heavy).
                    final boolean success = fetcher.fetchForAppId(appId, false);
                    if (success) {
                        refreshed++;
                    }
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
            caughtException = e;
        } finally {
            // Clear review-game cache after any successful refreshes, even if job aborted early
            if (refreshed > 0) {
                final Cache reviewGame = cacheManager.getCache("review-game");
                if (reviewGame != null) {
                    reviewGame.clear();
                    log.info("Cleared review-game cache after refreshing {} apps", refreshed);
                }
            }
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("SteamAppReviewsRefreshJob refreshed={} durationMs={}", refreshed, ms);
            if (caughtException != null) {
                throw new JobExecutionException(caughtException, false);
            }
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
