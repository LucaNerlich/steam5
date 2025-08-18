package org.steam5.job.events;

import lombok.Getter;
import org.steam5.job.blurhash.BlurhashEnqueueListener;

/**
 * Application event to request Blurhash encoding for a specific appId or steamId.
 */
@Getter
public final class BlurhashEncodeRequested {

    private final Long appId;
    private final String steamId;
    private final BlurhashEnqueueListener.Type type;

    public BlurhashEncodeRequested(Long appId, String steamId, final BlurhashEnqueueListener.Type type) {
        this.appId = appId;
        this.steamId = steamId;
        this.type = type;
    }

}


