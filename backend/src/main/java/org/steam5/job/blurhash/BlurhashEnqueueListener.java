package org.steam5.job.blurhash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.steam5.job.events.BlurhashEncodeRequested;

@Component
@Slf4j
@RequiredArgsConstructor
public class BlurhashEnqueueListener {

    private final Scheduler scheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlurhashEncodeRequested(BlurhashEncodeRequested event) {
        try {
            final JobDataMap dataMap = new JobDataMap();
            dataMap.put("appId", event.getAppId());
            scheduler.triggerJob(JobKey.jobKey("BlurhashScreenshotsJob"), dataMap);
            log.info("Enqueued BlurhashScreenshotsJob for appId={}", event.getAppId());
        } catch (SchedulerException se) {
            log.warn("Failed to enqueue BlurhashScreenshotsJob for appId {}: {}", event.getAppId(), se.getMessage());
        }
    }
}


