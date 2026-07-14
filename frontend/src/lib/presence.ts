/** Site-wide presence scope shared by all games (review, year, price). */
export const SITE_PRESENCE_SCOPE_KEY = "site";

/** Matches backend PresenceHandshakeInterceptor.SCOPE_KEY_PATTERN. */
export const SITE_PRESENCE_SCOPE_PATTERN = /^(site|\d{4}-\d{2}-\d{2}(:\d+:\d+)?)$/;
