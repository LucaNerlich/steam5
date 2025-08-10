package org.steam5.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppReviewsFetcher;

@Component
@ConditionalOnProperty(prefix = "steam.apps", name = "runOnStartup", havingValue = "true")
public class SteamAppReviewsStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SteamAppReviewsStartupRunner.class);

    private final SteamAppReviewsFetcher fetcher;

    public SteamAppReviewsStartupRunner(SteamAppReviewsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("steam.apps.runOnStartup=true -> starting one-time reviews ingestion on startup");
        try {
            fetcher.fetchAndStoreAllReviewSummaries();
            log.info("One-time reviews ingestion finished successfully");
        } catch (Exception ex) {
            log.error("One-time reviews ingestion failed", ex);
        }
    }
}


