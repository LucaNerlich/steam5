package org.steam5.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppListFetcher;

@Component
@ConditionalOnProperty(prefix = "steam.apps", name = "runOnStartup", havingValue = "true")
public class SteamAppListStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SteamAppListStartupRunner.class);

    private final SteamAppListFetcher fetcher;

    public SteamAppListStartupRunner(SteamAppListFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("steam.apps.runOnStartup=true -> starting one-time Steam app list ingestion on startup");
        try {
            fetcher.ingestFullListToDatabase();
            log.info("One-time Steam app list ingestion finished successfully");
        } catch (Exception ex) {
            log.error("One-time Steam app list ingestion failed", ex);
        }
    }
}


