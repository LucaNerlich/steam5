package org.steam5.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.steam5.config.SteamAppsConfig;
import org.steam5.service.SteamAppListFetcher;

@Component
@EnableConfigurationProperties(SteamAppsConfig.class)
public class SteamAppListDownloadJob {

    private static final Logger log = LoggerFactory.getLogger(SteamAppListDownloadJob.class);

    private final SteamAppListFetcher fetcher;

    public SteamAppListDownloadJob(SteamAppListFetcher fetcher) {
        this.fetcher = fetcher;
    }

    // Cron can be overridden by property steam.apps.downloadCron
    @Scheduled(cron = "${steam.apps.downloadCron:0 5 0 * * *}")
    public void runNightlyDownload() {
        try {
            fetcher.ingestFullListToDatabase();
            log.info("Steam app list ingestion completed successfully");
        } catch (Exception ex) {
            log.error("Steam app list download failed", ex);
        }
    }
}


