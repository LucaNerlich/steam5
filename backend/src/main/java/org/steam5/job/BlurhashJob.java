package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.domain.details.Screenshot;
import org.steam5.job.blurhash.BlurHash;
import org.steam5.repository.details.ScreenshotRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;

@Component
@Slf4j
@DisallowConcurrentExecution
public class BlurhashJob implements Job {

    private final ScreenshotRepository screenshotRepository;

    public BlurhashJob(final ScreenshotRepository screenshotRepository) {
        this.screenshotRepository = screenshotRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long startNs = System.nanoTime();
        int scanned = 0, encoded = 0, failed = 0;
        try {
            final int pageSize = 10; // small batch to limit memory
            int page = 0;
            boolean more = true;
            while (more) {
                var batch = screenshotRepository.findPageWithoutBlurhash(org.springframework.data.domain.PageRequest.of(page, pageSize));
                if (batch.isEmpty()) break;
                for (Screenshot s : batch.getContent()) {
                    scanned++;
                    // thumbnail
                    if ((s.getBlurhashThumb() == null || s.getBlurhashThumb().isBlank()) && s.getPathThumbnail() != null && !s.getPathThumbnail().isBlank()) {
                        try (InputStream in = URI.create(s.getPathThumbnail()).toURL().openStream()) {
                            BufferedImage img = ImageIO.read(in);
                            if (img == null) {
                                failed++;
                            } else {
                                String hash = BlurHash.encode(img, 4, 4);
                                s.setBlurhashThumb(hash);
                                encoded++;
                            }
                        } catch (Exception e) {
                            failed++;
                            log.warn("Blurhash encode failed (thumb) for screenshot id={} src={} err={}", s.getId(), s.getPathThumbnail(), e.toString());
                        }
                    }
                    // full
                    if ((s.getBlurhashFull() == null || s.getBlurhashFull().isBlank()) && s.getPathFull() != null && !s.getPathFull().isBlank()) {
                        try (InputStream in = URI.create(s.getPathFull()).toURL().openStream()) {
                            BufferedImage img = ImageIO.read(in);
                            if (img == null) {
                                failed++;
                            } else {
                                String hash = BlurHash.encode(img, 4, 4);
                                s.setBlurhashFull(hash);
                                encoded++;
                            }
                        } catch (Exception e) {
                            failed++;
                            log.warn("Blurhash encode failed (full) for screenshot id={} src={} err={}", s.getId(), s.getPathFull(), e.toString());
                        }
                    }
                    screenshotRepository.save(s);
                }
                page++;
                more = batch.hasNext();
                try {
                    System.gc();
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            log.error("BlurhashJob execution error", e);
            throw new JobExecutionException(e, false);
        } finally {
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            log.info("BlurhashJob finished scanned={} encoded={} failed={} durationMs={}", scanned, encoded, failed, ms);
        }
    }

    @Bean("BlurhashJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(BlurhashJob.class)
                .storeDurably()
                .withIdentity("BlurhashJob")
                .build();
    }
}


