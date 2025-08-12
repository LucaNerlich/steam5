package org.steam5.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.steam5.service.SteamAppDetailsFetcher;

@Component
public class SteamAppDetailFetchJob implements Job {

    private final SteamAppDetailsFetcher fetcher;

    public SteamAppDetailFetchJob(final SteamAppDetailsFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        fetcher.ingest();
    }
}
