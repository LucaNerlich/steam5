package org.steam5.job.blurhash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
            dataMap.put("steamId", event.getSteamId());

            if (event.getAppId() != null && StringUtils.isNotBlank(event.getSteamId())) {
                throw new IllegalArgumentException("Only one of appId or steamId can be specified");
            }

            switch (event.getType()) {
                case SCREENSHOT -> {
                    scheduler.triggerJob(JobKey.jobKey("BlurhashScreenshotsJob"), dataMap);
                    log.info("Enqueued BlurhashScreenshotsJob for appId={}", event.getAppId());
                }
                case AVATAR -> {
                    scheduler.triggerJob(JobKey.jobKey("BlurhashAvatarJob"), dataMap);
                    log.info("Enqueued BlurhashAvatarJob for  steamId={}", event.getSteamId());
                }
                default -> throw new IllegalArgumentException("Unknown type " + event.getType());
            }
        } catch (SchedulerException se) {
            log.warn("Failed to enqueue Blurhash Job for appId {} or steamId={}: {}", event.getAppId(), event.getSteamId(), se.getMessage());
        }
    }

    public enum Type {
        SCREENSHOT,
        AVATAR
    }
}


