package org.steam5.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.steam5.service.RoundPresenceService;

/**
 * Periodically prunes closed or idle presence sessions and rebroadcasts updated counts.
 */
@Component
@RequiredArgsConstructor
public class PresenceMaintenanceScheduler {

    private final RoundPresenceService presenceService;

    @Scheduled(fixedDelayString = "${presence.sweep-interval-ms:30000}")
    public void sweepPresenceSessions() {
        presenceService.sweepIdleAndClosed();
    }
}
