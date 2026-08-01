package org.steam5.domain;

/**
 * Hardcoded allowlist for comment moderation (archive) actions.
 * Authorization is always re-checked server-side; the frontend only uses this for UI.
 */
public final class CommentModerator {

    /** Steam ID permitted to archive comments. */
    public static final String STEAM_ID = "76561198028075069";

    private CommentModerator() {
    }

    public static boolean isModerator(final String steamId) {
        return STEAM_ID.equals(steamId);
    }
}
