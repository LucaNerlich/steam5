package org.steam5.service;

import org.springframework.stereotype.Service;
import org.steam5.security.FixedWindowCounter;

/**
 * Fixed-window per-user rate limiter for comment posts and reaction toggles.
 */
@Service
public class CommentRateLimiter {

    private static final int COMMENT_LIMIT_PER_MINUTE = 5;
    private static final int REACTION_LIMIT_PER_MINUTE = 30;

    private final FixedWindowCounter commentsByUser = new FixedWindowCounter(COMMENT_LIMIT_PER_MINUTE);
    private final FixedWindowCounter reactionsByUser = new FixedWindowCounter(REACTION_LIMIT_PER_MINUTE);

    /**
     * Determines whether a user may submit another comment within the current minute.
     *
     * @param steamId the user's Steam ID; null or blank IDs bypass the rate limit
     * @return {@code true} if the user is within the five-comment limit, {@code false} otherwise
     */
    public boolean tryAcquireComment(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return commentsByUser.tryAcquire(steamId);
    }

    /**
     * Determines whether a user may submit another reaction within the current minute.
     *
     * @param steamId the user's Steam ID; null or blank IDs bypass rate limiting
     * @return {@code true} if the user is within the per-minute reaction limit, {@code false} otherwise
     */
    public boolean tryAcquireReaction(final String steamId) {
        if (steamId == null || steamId.isBlank()) return true;
        return reactionsByUser.tryAcquire(steamId);
    }
}
