package org.steam5.job;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.steam5.repository.SteamAppReviewsRepository;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.SteamAppReviewsFetcher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The nightly refresh must drop the aggregate review-game cache exactly once after its loop,
 * not once per app and not at all when nothing was refreshed.
 */
class SteamAppReviewsRefreshJobTest {

    private final SteamAppReviewsFetcher fetcher = mock(SteamAppReviewsFetcher.class);
    private final SteamAppReviewsRepository reviewsRepository = mock(SteamAppReviewsRepository.class);
    private final DomainCacheEvictor cacheEvictor = mock(DomainCacheEvictor.class);
    private final SteamAppReviewsRefreshJob job =
            new SteamAppReviewsRefreshJob(fetcher, reviewsRepository, cacheEvictor, 10);

    private static JobExecutionContext context() {
        final JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        return context;
    }

    @Test
    void clearsTheAggregateCacheOnceAfterASuccessfulRefresh() throws Exception {
        when(reviewsRepository.findIdsOrderByUpdatedAtAsc(any())).thenReturn(List.of(1L, 2L, 3L));
        when(fetcher.fetchForAppId(any(), any(Boolean.class))).thenReturn(true);

        job.execute(context());

        verify(cacheEvictor).evictReviewGameState();
    }

    @Test
    void doesNotClearWhenNothingWasRefreshed() throws Exception {
        when(reviewsRepository.findIdsOrderByUpdatedAtAsc(any())).thenReturn(List.of());

        job.execute(context());

        verify(cacheEvictor, never()).evictReviewGameState();
    }
}
