package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.steam5.domain.details.Screenshot;
import org.steam5.job.blurhash.BlurHash;
import org.steam5.repository.details.ScreenshotRepository;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;

@Component
@Slf4j
@DisallowConcurrentExecution
public class BlurhashJob implements Job {

    private final ScreenshotRepository screenshotRepository;

    public BlurhashJob(final ScreenshotRepository screenshotRepository) {
        this.screenshotRepository = screenshotRepository;
    }

    public void encodeForApp(Long appId) {
        int scanned = 0, encoded = 0, failed = 0;
        try {
            var list = screenshotRepository.findMissingForApp(appId);
            for (var s : list) {
                scanned++;
                final Counters c = encodeAndStore(s);
                encoded += c.encoded;
                failed += c.failed;
            }
            log.info("Blurhash immediate finished for appId={} scanned={} encoded={} failed={}", appId, scanned, encoded, failed);
        } catch (Exception e) {
            log.warn("Blurhash immediate failed for appId={} err={}", appId, e.toString());
        }
    }

    private static String toPngDataUrl(BufferedImage src, int w, int h) {
        try {
            final BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(src, 0, 0, w, h, null);
            g2.dispose();
            ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            final String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Job start BlurhashJob key={} fireTime={} scheduled={} refireCount={}", context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());

        long start = System.nanoTime();
        int scanned = 0, encoded = 0, failed = 0;
        try {
            final int pageSize = 10; // small batch to limit memory
            int page = 0;
            boolean more = true;
            while (more) {
                final Page<Screenshot> batch = screenshotRepository.findPageWithoutBlurhash(PageRequest.of(page, pageSize));
                if (batch.isEmpty()) break;
                for (Screenshot s : batch.getContent()) {
                    scanned++;
                    Counters c = encodeAndStore(s);
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
            log.error("BlurhashJob execution error", e);
            throw new JobExecutionException(e, false);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            log.info("BlurhashJob finished scanned={} encoded={} failed={} durationMs={}", scanned, encoded, failed, ms);
        }
    }

    @Bean("BlurhashJob")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(BlurhashJob.class).storeDurably().withIdentity("BlurhashJob").build();
    }

    private Encoded readAndEncode(String url, int w, int h) {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            final BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            final String hash = BlurHash.encode(img, 4, 4);
            final String data = toPngDataUrl(img, w, h);
            return new Encoded(hash, data);
        } catch (Exception e) {
            return null;
        }
    }

    private Counters encodeAndStore(Screenshot s) {
        Counters c = new Counters();
        boolean changed = false;
        // thumb
        if ((s.getBlurhashThumb() == null || s.getBlurhashThumb().isBlank()) && s.getPathThumbnail() != null && !s.getPathThumbnail().isBlank()) {
            Encoded enc = readAndEncode(s.getPathThumbnail(), 32, 20);
            if (enc == null) {
                c.failed++;
            } else {
                s.setBlurhashThumb(enc.hash());
                if (enc.dataUrl() != null) s.setBlurdataThumb(enc.dataUrl());
                c.encoded++;
                changed = true;
            }
        }
        // full
        if ((s.getBlurhashFull() == null || s.getBlurhashFull().isBlank()) && s.getPathFull() != null && !s.getPathFull().isBlank()) {
            Encoded enc = readAndEncode(s.getPathFull(), 64, 36);
            if (enc == null) {
                c.failed++;
            } else {
                s.setBlurhashFull(enc.hash());
                if (enc.dataUrl() != null) s.setBlurdataFull(enc.dataUrl());
                c.encoded++;
                changed = true;
            }
        }
        if (changed) {
            try {
                screenshotRepository.save(s);
            } catch (Exception ignored) {
            }
        }
        return c;
    }

    private record Encoded(String hash, String dataUrl) {
    }

    private static class Counters {
        int encoded;
        int failed;
    }
}


