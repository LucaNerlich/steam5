package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.steam5.domain.details.Screenshot;
import org.steam5.repository.details.ScreenshotRepository;
import org.steam5.service.BlurhashService;

import java.util.List;

@Component
@Slf4j
public class BlurhashScreenshotsJob implements Job {

    private final BlurhashService service;
    private final CacheManager cacheManager;
    private final ScreenshotRepository screenshotRepository;

    public BlurhashScreenshotsJob(final BlurhashService service, final CacheManager cacheManager, final ScreenshotRepository screenshotRepository) {
        this.service = service;
        this.cacheManager = cacheManager;
        this.screenshotRepository = screenshotRepository;
    }

    /**
     * Encodes missing screenshots for a given application into BlurHash format.
     *
     * This method processes all missing screenshots associated with the specified
     * application ID. It scans each screenshot, and if conditions are met (i.e.,
     * paths are available and BlurHash values are unset), it encodes them using
     * the BlurhashService to generate compact image representations. The encoded
     * data is then stored in the database.
     *
     * @param appId The unique identifier of the application whose screenshots need to be processed.
     * @return The total number of successfully encoded screenshots for the specified application ID.
     */
    int encodeForApp(Long appId) {
        int scanned = 0, encoded = 0, failed = 0;
        try {
            final List<Screenshot> list = screenshotRepository.findMissingForApp(appId);
            for (Screenshot s : list) {
                scanned++;
                final Counters c = handleScreenshot(s);
                encoded += c.encoded;
                failed += c.failed;
            }
            log.info("Blurhash immediate finished for appId={} scanned={} encoded={} failed={}", appId, scanned, encoded, failed);
        } catch (Exception e) {
            log.warn("Blurhash immediate failed for appId={} err={}", appId, e.toString());
        }
        return encoded;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Job start BlurhashScreenshotsJob key={} fireTime={} scheduled={} refireCount={}", context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());

        // When executed programmatically, we can optionally specify an appId to limit the scope of the job.
        final Object appIdObj = context.getMergedJobDataMap() != null ? context.getMergedJobDataMap().get("appId") : null;
        if (appIdObj != null) {
            final Long appId = toLong(appIdObj);
            if (appId != null) {
                try {
                    int encoded = encodeForApp(appId);
                    if (encoded > 0) {
                        final Cache cache = cacheManager.getCache("review-game");
                        if (cache != null) {
                            cache.clear();
                        }
                    }
                    log.info("Targeted BlurhashScreenshotsJob finished for appId={} encoded={}", appId, encoded);
                } catch (Exception e) {
                    log.warn("Targeted BlurhashScreenshotsJob failed for appId {}: {}", appId, e.getMessage());
                }
                return;
            }
        }

        // Fallback 'full-run' job, which encodes all screenshots.
        long start = System.nanoTime();
        int scanned = 0, encoded = 0, failed = 0;
        try {
            final int pageSize = 10; // small batch to limit memory
            int page = 0;
            boolean more = true;
            while (more) {
                final Page<Screenshot> batch = screenshotRepository.findPageWithoutBlurhash(PageRequest.of(page, pageSize));
                if (batch.isEmpty()) break;
                for (Screenshot screenshot : batch.getContent()) {
                    scanned++;
                    final Counters c = handleScreenshot(screenshot);
                    encoded += c.encoded;
                    failed += c.failed;
                }
                page++;
                more = batch.hasNext();
                try {
                    System.gc();
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            log.error("BlurhashScreenshotsJob execution error", e);
            throw new JobExecutionException(e, false);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            log.info("BlurhashScreenshotsJob finished scanned={} encoded={} failed={} durationMs={}", scanned, encoded, failed, ms);

            // Clear game cache, to allow the FE to pull new screenshot data.
            if (encoded > 0) {
                final Cache cache = cacheManager.getCache("review-game");
                if (cache != null) {
                    cache.clear();
                }
            }
        }
    }

    private Long toLong(Object obj) {
        try {
            if (obj instanceof Number) return ((Number) obj).longValue();
            if (obj instanceof String s) return Long.parseLong(s);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Bean("BlurhashScreenshotsJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob()
                .ofType(BlurhashScreenshotsJob.class)
                .storeDurably()
                .withIdentity("BlurhashScreenshotsJob")
                .build();
    }


    /**
     * Handles the encoding of screenshots to generate BlurHash representations for thumbnails and full images.
     *
     * This method checks if a screenshot requires processing by evaluating whether its thumbnail or full image
     * paths are available and if their corresponding BlurHash values are not yet set. If conditions are met, it
     * encodes the image data using the specified dimensions and updates the screenshot object with the generated
     * BlurHash strings and optional PNG data URLs.
     *
     * @param screenshot The screenshot to be processed for encoding its images into BlurHash format.
     * @return A Counters object indicating the number of successful encodings and failures encountered during processing.
     */
    private Counters handleScreenshot(Screenshot screenshot) {
        final Counters counters = new Counters();
        boolean changed = false;

        // thumb
        if ((screenshot.getBlurhashThumb() == null || screenshot.getBlurhashThumb().isBlank()) && screenshot.getPathThumbnail() != null && !screenshot.getPathThumbnail().isBlank()) {
            final BlurhashService.Encoded enc = service.readAndEncode(screenshot.getPathThumbnail(), BlurhashService.Type.THUMBNAIL);
            if (enc == null) {
                counters.failed++;
            } else {
                screenshot.setBlurhashThumb(enc.hash());
                if (enc.dataUrl() != null) screenshot.setBlurdataThumb(enc.dataUrl());
                counters.encoded++;
                changed = true;
            }
        }

        // full
        if ((screenshot.getBlurhashFull() == null || screenshot.getBlurhashFull().isBlank()) && screenshot.getPathFull() != null && !screenshot.getPathFull().isBlank()) {
            final BlurhashService.Encoded encoded = service.readAndEncode(screenshot.getPathFull(), BlurhashService.Type.FULL);
            if (encoded == null) {
                counters.failed++;
            } else {
                screenshot.setBlurhashFull(encoded.hash());
                if (encoded.dataUrl() != null) screenshot.setBlurdataFull(encoded.dataUrl());
                counters.encoded++;
                changed = true;
            }
        }
        if (changed) {
            try {
                screenshotRepository.save(screenshot);
            } catch (Exception ignored) {
            }
        }
        return counters;
    }

    private static class Counters {
        int encoded;
        int failed;
    }
}


