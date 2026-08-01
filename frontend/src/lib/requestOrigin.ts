/**
 * Trusted site origin for browser CSRF checks (no trailing slash).
 */
export function trustedSiteOrigin(): string {
    return (process.env.NEXT_PUBLIC_DOMAIN || "https://steam5.org").replace(/\/$/, "");
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

function isAllowedOrigin(candidate: string, trusted: string): boolean {
    if (originsMatch(candidate, trusted)) return true;
    try {
        // Local Next.js → backend proxy during development.
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
