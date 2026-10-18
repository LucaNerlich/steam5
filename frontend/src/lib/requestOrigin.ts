import {NextResponse} from "next/server";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

/**
 * Trusted site origin for browser CSRF checks (no trailing slash).
 */
export function trustedSiteOrigin(): string {
    return (process.env.NEXT_PUBLIC_DOMAIN || "https://steam5.org").replace(/\/$/, "");
}

/** Strip control chars and bound length before logging untrusted header/path values. */
function sanitizeForLog(value: string | null | undefined, maxLen = 200): string {
    if (value == null || value === "") return "";
    return value.replace(/[\u0000-\u001f\u007f]/g, "").slice(0, maxLen);
}

function isLoopbackHostname(hostname: string): boolean {
    return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "[::1]";
}

/**
 * True when an absolute origin (or URL) points at loopback.
 *
 * <p>`URL#hostname` keeps the brackets around IPv6 literals, so the check is for `[::1]`, not
 * `::1`. Two api routes had their own copies comparing `::1`, which could never match — an
 * IPv6-loopback backend therefore silently lost its loopback exemption (no WS ticket, no
 * server-side logout revocation).</p>
 */
export function isLoopbackOrigin(origin: string): boolean {
    try {
        return isLoopbackHostname(new URL(origin).hostname);
    } catch {
        return false;
    }
}

function originsMatch(candidate: string, trusted: string): boolean {
    try {
        const a = new URL(candidate);
        const b = new URL(trusted);
        return a.origin === b.origin;
    } catch {
        return false;
    }
}

function isNonProduction(): boolean {
    return process.env.NODE_ENV !== "production";
}

/**
 * Normalizes an absolute http(s) origin, or null when the value is not one (credentials, a
 * path, a query, a hash or a non-http scheme). Both callers must agree on what "the same
 * origin" means before comparing against the trusted list.
 */
export function normalizedHttpOrigin(value: string): string | null {
    try {
        const url = new URL(value);
        if ((url.protocol !== 'http:' && url.protocol !== 'https:')
            || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
            return null;
        }
        return url.origin;
    } catch {
        return null;
    }
}

/**
 * Returns the normalized backend origin when a bearer token may cross plain HTTP to it, or null
 * when it may not: https always qualifies, as do loopback and any origin listed in
 * `ALLOWED_BACKEND_ORIGINS` outside production. Comparison is normalized on both sides, so a
 * trailing slash, an upper-case scheme or an explicit default port cannot make the same origin
 * look untrusted in one route and trusted in another.
 */
export function allowedBackendOrigin(
    configuredOrigin: string,
    production = process.env.NODE_ENV === 'production',
    trustedOrigins = process.env.ALLOWED_BACKEND_ORIGINS || '',
): string | null {
    const origin = normalizedHttpOrigin(configuredOrigin);
    if (!origin) return null;
    if (!production || origin.startsWith('https://') || isLoopbackOrigin(origin)) return origin;

    const trusted = trustedOrigins.split(',')
        .map(value => normalizedHttpOrigin(value.trim()))
        .filter((value): value is string => value !== null);
    return trusted.includes(origin) ? origin : null;
}

function isAllowedOrigin(candidate: string, trusted: string): boolean {
    if (originsMatch(candidate, trusted)) return true;
    if (!isNonProduction()) return false;
    try {
        // Local Next.js → backend proxy during development / non-prod only.
        return isLoopbackHostname(new URL(candidate).hostname);
    } catch {
        return false;
    }
}

/**
 * Returns true when the request Origin (preferred) or Referer matches the app origin.
 * Used to reject cross-site cookie-authenticated POSTs.
 */
export function isTrustedBrowserOrigin(headers: Headers): boolean {
    const trusted = trustedSiteOrigin();
    const origin = headers.get("origin");
    if (origin) {
        return isAllowedOrigin(origin, trusted);
    }
    const referer = headers.get("referer");
    if (!referer) return false;
    try {
        return isAllowedOrigin(new URL(referer).origin, trusted);
    } catch {
        return false;
    }
}

/**
 * Returns a 403 no-store response when Origin/Referer is untrusted; otherwise null.
 * Logs a sanitized commentId/origin/referer under the given route tag.
 */
export function rejectUntrustedOrigin(
    headers: Headers,
    routeTag: string,
    commentId: string,
): NextResponse | null {
    if (isTrustedBrowserOrigin(headers)) return null;
    console.error(`[${routeTag}] Rejected untrusted origin`, {
        commentId: sanitizeForLog(commentId, 64),
        origin: sanitizeForLog(headers.get("origin")),
        referer: sanitizeForLog(headers.get("referer")),
    });
    return NextResponse.json(
        {error: "forbidden"},
        {status: 403, headers: NO_STORE},
    );
}
