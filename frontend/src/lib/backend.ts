/**
 * Single source of truth for the backend origin. All server-side pages and lib
 * utilities fetch through this constant so changing the default (e.g. in tests or
 * a different deploy target) is a one-file edit.
 *
 * In production the env-var origin MUST be HTTPS; a clear error is thrown at
 * import time so the misconfiguration is caught immediately on server boot.
 */
export const BACKEND_ORIGIN = (() => {
    const origin = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    if (process.env.NODE_ENV === 'production' && !origin.startsWith('https://')) {
        throw new Error(
            `NEXT_PUBLIC_API_DOMAIN must use HTTPS in production. Got: ${origin}`,
        );
    }
    return origin;
})();
