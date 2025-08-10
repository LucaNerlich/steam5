package org.steam5.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.steam5.config.SteamAppsConfig;
import org.steam5.service.SteamAppReviewsFetcher;

@Component
@EnableConfigurationProperties(SteamAppsConfig.class)
public class SteamAppReviewsJob {

    private static final Logger log = LoggerFactory.getLogger(SteamAppReviewsJob.class);

    private final SteamAppReviewsFetcher fetcher;

    public SteamAppReviewsJob(SteamAppReviewsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Scheduled(cron = "${steam.apps.downloadCron:0 5 0 * * *}")
    public void runNightly() {
        try {
            fetcher.fetchAndStoreAllReviewSummaries();
            log.info("Steam app reviews ingestion completed successfully");
        } catch (Exception ex) {
            log.error("Steam app reviews ingestion failed", ex);
        }
    }
}


