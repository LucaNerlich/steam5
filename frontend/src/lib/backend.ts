/**
 * Single source of truth for the backend origin. All server-side pages and lib
 * utilities fetch through this constant so changing the default (e.g. in tests or
 * a different deploy target) is a one-file edit.
 */
export const BACKEND_ORIGIN =
    process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
