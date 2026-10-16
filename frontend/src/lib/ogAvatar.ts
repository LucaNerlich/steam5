/**
 * Host allow-list for avatar images that a server-side renderer (next/og's satori) will
 * fetch. satori fetches any non-data-URI `<img src>` with no allow-list of its own, and the
 * value comes from the backend's stored Steam avatar URL — which is not otherwise validated.
 */

const ALLOWED_AVATAR_HOSTS = [/(^|\.)steamstatic\.com$/i, /(^|\.)akamaihd\.net$/i];

/** Returns the avatar URL when it is https and on a Steam CDN host, otherwise null. */
export function sanitizeAvatarUrl(raw: string | null | undefined): string | null {
    if (!raw) return null;
    try {
        const parsed = new URL(raw);
        return parsed.protocol === "https:" && ALLOWED_AVATAR_HOSTS.some((re) => re.test(parsed.hostname))
            ? raw
            : null;
    } catch {
        return null;
    }
}
