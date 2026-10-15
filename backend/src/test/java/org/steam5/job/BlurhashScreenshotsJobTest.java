package org.steam5.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.steam5.config.JobsConfig;
import org.steam5.domain.IngestState;
import org.steam5.domain.details.Screenshot;
import org.steam5.repository.IngestStateRepository;
import org.steam5.repository.details.ScreenshotRepository;
import org.steam5.service.BlurhashService;
import org.steam5.service.DomainCacheEvictor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The full-run keyset cursor is persisted in {@code ingest_state}. A local cursor
 * restarted at the queue head on every run, so rows whose image can never be
 * encoded consumed the whole batch and starved every later screenshot.
 */
class BlurhashScreenshotsJobTest {

    private static final String CURSOR_ID = "blurhash_screenshots";

    private ScreenshotRepository screenshotRepository;
    private IngestStateRepository ingestStateRepository;
    private BlurhashScreenshotsJob job;

    @BeforeEach
    void setUp() {
        final BlurhashService service = mock(BlurhashService.class);
        final CacheManager cacheManager = mock(CacheManager.class);
        screenshotRepository = mock(ScreenshotRepository.class);
        final DomainCacheEvictor cacheEvictor = mock(DomainCacheEvictor.class);
        final JobsConfig jobsConfig = mock(JobsConfig.class);
        final JobsConfig.Blurhash blurhash = mock(JobsConfig.Blurhash.class);
        when(jobsConfig.getBlurhash()).thenReturn(blurhash);
        when(blurhash.getBatchLimit()).thenReturn(100);
        ingestStateRepository = mock(IngestStateRepository.class);
        job = new BlurhashScreenshotsJob(service, cacheManager, screenshotRepository, cacheEvictor,
                jobsConfig, ingestStateRepository);
    }

    @Test
    void fullRun_resumesFromThePersistedCursorAndResetsItWhenTheQueueIsEmpty() throws Exception {
        final IngestState state = mock(IngestState.class);
        when(state.getLastAppId()).thenReturn(500L);
        when(ingestStateRepository.findById(CURSOR_ID)).thenReturn(Optional.of(state));

        // A full page keeps the loop going; the following empty page exhausts the queue.
        final List<Screenshot> fullPage = new ArrayList<>();
        for (long id = 501L; id <= 510L; id++) {
            fullPage.add(screenshot(id));
        }
        when(screenshotRepository.findPageWithoutBlurhash(eq(500L), any(PageRequest.class))).thenReturn(fullPage);
        when(screenshotRepository.findPageWithoutBlurhash(eq(510L), any(PageRequest.class))).thenReturn(List.of());

        job.execute(contextWithoutAppId());

        // Resumed from the persisted cursor instead of restarting at the queue head.
        verify(screenshotRepository).findPageWithoutBlurhash(eq(500L), any(PageRequest.class));
        // The cursor was persisted as it advanced...
        verify(ingestStateRepository).upsert(eq(CURSOR_ID), eq(510L), any(OffsetDateTime.class));
        // ...and reset once the queue was exhausted so the next pass retries failures.
        verify(ingestStateRepository).upsert(eq(CURSOR_ID), eq(0L), any(OffsetDateTime.class));
    }

    @Test
    void fullRun_startsAtZeroAndWritesNothingWhenNoCursorIsPersisted() throws Exception {
        when(ingestStateRepository.findById(CURSOR_ID)).thenReturn(Optional.empty());
        when(screenshotRepository.findPageWithoutBlurhash(anyLong(), any(PageRequest.class))).thenReturn(List.of());

        job.execute(contextWithoutAppId());

        verify(screenshotRepository).findPageWithoutBlurhash(eq(0L), any(PageRequest.class));
        verify(ingestStateRepository, never()).upsert(any(), anyLong(), any(OffsetDateTime.class));
    }

    private static Screenshot screenshot(long id) {
        final Screenshot screenshot = mock(Screenshot.class);
        when(screenshot.getId()).thenReturn(id);
        return screenshot;
    }

    /** A context with no {@code appId}, i.e. the full-run path. */
    private static JobExecutionContext contextWithoutAppId() {
        final JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        final JobDetail detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(JobKey.jobKey("BlurhashScreenshotsJob"));
        when(context.getJobDetail()).thenReturn(detail);
        return context;
    }
}
