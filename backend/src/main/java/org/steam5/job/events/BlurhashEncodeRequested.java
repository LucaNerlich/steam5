package org.steam5.job.events;

import lombok.Getter;

/**
 * Application event to request Blurhash encoding for a specific appId.
 */
@Getter
public final class BlurhashEncodeRequested {

    private final Long appId;

    public BlurhashEncodeRequested(Long appId) {
        this.appId = appId;
    }

}


