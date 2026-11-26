package org.steam5.job;

import org.junit.jupiter.api.Test;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.PlayerSpotlightService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * The eviction must run after the transactional write: evicting first would let a concurrent
 * profile read repopulate the cache with the pre-job list, and dropping the call leaves the
 * featured player without today's entry for the rest of the TTL.
 */
class PlayerSpotlightJobTest {

    @Test
    void evictsOnlyAfterTheSpotlightIsComputed() throws Exception {
        final PlayerSpotlightService service = mock(PlayerSpotlightService.class);
        final DomainCacheEvictor evictor = mock(DomainCacheEvictor.class);

        new PlayerSpotlightJob(service, evictor).execute(null);

        final var order = inOrder(service, evictor);
        order.verify(service).computeAndPersistForToday();
        order.verify(evictor).evictPlayerSpotlights();
    }
}
