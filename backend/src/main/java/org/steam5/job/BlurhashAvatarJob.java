package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@DisallowConcurrentExecution
public class BlurhashAvatarJob implements Job {

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        log.info("Job start BlurhashAvatarJob key={} fireTime={} scheduled={} refireCount={}", context.getJobDetail().getKey(), context.getFireTime(), context.getScheduledFireTime(), context.getRefireCount());

    }
}
