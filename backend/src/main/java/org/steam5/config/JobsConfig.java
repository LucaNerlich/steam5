package org.steam5.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Per-run batch limits for background ingest/maintenance jobs. Limits how much work
 * each scheduled execution performs so small VPS heaps are not exhausted in one pass.
 * Cursors in {@code ingest_state} resume on the next run.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jobs")
public class JobsConfig {

    private SteamAppList steamAppList = new SteamAppList();
    private SteamAppReviews steamAppReviews = new SteamAppReviews();
    private SteamAppDetails steamAppDetails = new SteamAppDetails();
    private Blurhash blurhash = new Blurhash();

    @Getter
    @Setter
    public static class SteamAppList {
        /** Max Steam API list pages per run (each page may contain up to 50k apps). */
        private int batchLimit = 1;
    }

    @Getter
    @Setter
    public static class SteamAppReviews {
        /** Max apps whose review counts are fetched per ingest run. */
        private int batchLimit = 1000;
    }

    @Getter
    @Setter
    public static class SteamAppDetails {
        /** Max apps whose store details are fetched per ingest run. */
        private int batchLimit = 500;
    }

    @Getter
    @Setter
    public static class Blurhash {
        /** Max screenshots encoded per full blurhash job run. */
        private int batchLimit = 100;
    }
}
