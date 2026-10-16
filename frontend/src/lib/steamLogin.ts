/**
 * Builds the Steam login URL with the configured authentication callback.
 *
 * @returns The Steam login endpoint URL with an encoded callback URL.
 */
export function buildSteamLoginUrl(): string {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const envBase = process.env.NEXT_PUBLIC_DOMAIN;
    const callbackBase = (typeof window !== 'undefined' && (!envBase || envBase.length === 0)) ? window.location.origin : (envBase || '');
    const callback = `${callbackBase}/api/auth/steam/callback`;
    return `${backend}/api/auth/steam/login?redirect=${encodeURIComponent(callback)}`;
}

/**
 * Starts the Steam login flow with the CSRF state token the callback requires.
 *
 * The token is minted here, stored in a short-lived client-side cookie, and appended to
 * the login URL; the backend embeds it in `openid.return_to` so Steam carries it back,
 * and the Next.js callback route verifies the URL param matches the cookie before it
 * proceeds. Every login entry point must use this — navigating with a bare
 * {@link buildSteamLoginUrl} is rejected by the callback as a state mismatch.
 */
export function startSteamLogin(): void {
    const state = crypto.randomUUID().replace(/-/g, '');
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `s5_state=${state}; path=/; max-age=300; samesite=lax${secure}`;
    window.location.href = `${buildSteamLoginUrl()}&state=${encodeURIComponent(state)}`;
}
