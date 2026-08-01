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
