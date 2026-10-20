import {NextResponse} from "next/server";
import type {NextRequest} from "next/server";
import {allowedBackendOrigin} from "./requestOrigin";

export const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

/**
 * Authorization header for the session token, or null when BACKEND_ORIGIN may not
 * receive a bearer token (plain HTTP outside loopback/allow-list in production).
 * Every route that sends the token must use this instead of formatting the header itself.
 */
export function bearerAuthHeaders(token: string): {authorization: string} | null {
    return allowedBackendOrigin(BACKEND_ORIGIN) ? {authorization: `Bearer ${token}`} : null;
}

/**
 * Relays the forwarded client IP set by the trusted edge proxy (Traefik/Coolify)
 * in front of this server so the backend's per-IP rate limiting sees the real
 * caller instead of this Next server's own outbound connection IP.
 *
 * The backend only honors X-Forwarded-For from trusted internal peers
 * (`server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies`),
 * so relaying the header here cannot be abused by clients that reach this server
 * directly.
 */
export function forwardedForHeaders(req: NextRequest): Record<string, string> {
    return forwardedForHeaderValue(req.headers.get("x-forwarded-for"));
}

/**
 * Builds the relay header from a raw `x-forwarded-for` value.
 *
 * Server components read the incoming header through `headers()` rather than a
 * `NextRequest`, so the value is passed in directly — both paths must agree on
 * the relay contract documented above.
 *
 * @param xff - the incoming `x-forwarded-for` value, or null/undefined when the edge did not set one
 * @returns the header to forward, or an empty object when there is nothing to relay
 */
export function forwardedForHeaderValue(xff: string | null | undefined): Record<string, string> {
    return xff ? {"x-forwarded-for": xff} : {};
}

/** Default bound for an upstream proxy call: a stalled backend must not hold the route open. */
export const BACKEND_TIMEOUT_MS = 10_000;

export type BackendFetchOptions = {
    /** `next.revalidate` window; omit for an uncached per-request read. */
    revalidate?: number;
    /** Cache tags for a revalidating fetch. */
    tags?: string[];
};

/**
 * Server-side backend GET for page prefetches: bounded, JSON, null on any failure.
 * Returns the response headers too, since some pages relay a freshness header.
 */
export async function fetchBackendJson<T>(
    path: string,
    options: BackendFetchOptions = {},
): Promise<{data: T | null; headers: Headers}> {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}${path}`, {
            headers: {accept: "application/json"},
            ...(options.revalidate === undefined
                ? {}
                : {next: {revalidate: options.revalidate, ...(options.tags ? {tags: options.tags} : {})}}),
            signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
        });
        if (!res.ok) return {data: null, headers: res.headers};
        return {data: await res.json() as T, headers: res.headers};
    } catch {
        // Build-time or backend outage: callers render their fallback.
        return {data: null, headers: new Headers()};
    }
}

export type ProxyOptions = {
    /** Backend path, query string included (e.g. `/api/leaderboard/all`). */
    path: string;
    /** Shared-cache window in seconds; omit for per-request routes. Never applied to errors. */
    revalidate?: number;
    /** Cache tags for a revalidating fetch (e.g. `["stats:game"]`). */
    tags?: string[];
    /** Backend response headers to relay (e.g. `X-Leaderboard-Refreshed-At`). */
    passThroughHeaders?: string[];
    /** Message for the generic error body and the 502 fallback. */
    errorMessage: string;
    /** `Cache-Control` for a successful response only. */
    cacheControl?: string;
    /** Bound for the upstream call; defaults to {@link BACKEND_TIMEOUT_MS}. */
    timeoutMs?: number;
    /** Session token to send as a bearer header; the trusted-origin gate runs first. */
    bearerToken?: string;
    /** Send `Cache-Control: private, no-store` on both success and error. */
    privateNoStore?: boolean;
    /** `Cache-Control` for an error response; defaults to `no-store`. */
    errorCacheControl?: string;
    /** Emit an RFC 9457 problem+json body on upstream errors (perfect-days contract). */
    problemJsonOnError?: boolean;
};

/**
 * Proxies one backend JSON endpoint: forwards `accept`, relays the caller's IP only on
 * per-request routes, bounds the upstream call, passes the status and payload through, and
 * turns a failure into a logged 502.
 *
 * The forwarded-IP rule matters for caching: Next hashes the request headers into the fetch
 * data-cache key, so relaying a per-client `x-forwarded-for` on a `revalidate` route turns one
 * shared cache entry into one entry per client and calls the backend once per visitor.
 */
export async function proxyBackendJson(req: NextRequest, options: ProxyOptions): Promise<NextResponse> {
    const {
        path,
        revalidate,
        tags,
        passThroughHeaders = [],
        errorMessage,
        cacheControl,
        timeoutMs = BACKEND_TIMEOUT_MS,
        bearerToken,
        privateNoStore,
        errorCacheControl = "no-store",
        problemJsonOnError,
    } = options;

    const headers: Record<string, string> = {accept: "application/json"};
    if (bearerToken) {
        const auth = bearerAuthHeaders(bearerToken);
        if (!auth) {
            return NextResponse.json({error: errorMessage}, {
                status: 503,
                headers: {"Cache-Control": "private, no-store"},
            });
        }
        Object.assign(headers, auth);
    }
    if (revalidate === undefined) {
        Object.assign(headers, forwardedForHeaders(req));
    }

    try {
        const res = await fetch(`${BACKEND_ORIGIN}${path}`, {
            headers,
            ...(revalidate === undefined ? {} : {next: {revalidate, ...(tags ? {tags} : {})}}),
            signal: AbortSignal.timeout(timeoutMs),
        });

        const relayed: Record<string, string> = {};
        for (const name of passThroughHeaders) {
            const value = res.headers.get(name);
            if (value) relayed[name] = value;
        }

        if (!res.ok) {
            // Pass the upstream payload through with its status, falling back to a generic body
            // when it is not JSON. Errors are logged once and never publicly cacheable.
            const errorBody: unknown = await res.json().catch(() => null);
            console.error(`[proxy] ${path} responded ${res.status}`);
            relayed["Cache-Control"] = privateNoStore ? "private, no-store" : errorCacheControl;
            if (problemJsonOnError) {
                return NextResponse.json(
                    {type: "about:blank", title: "Bad Gateway", status: res.status, detail: errorMessage},
                    {status: res.status, headers: {...relayed, "content-type": "application/problem+json"}},
                );
            }
            return NextResponse.json(errorBody ?? {error: errorMessage}, {status: res.status, headers: relayed});
        }

        const data = await res.json();
        if (privateNoStore) relayed["Cache-Control"] = "private, no-store";
        else if (cacheControl) relayed["Cache-Control"] = cacheControl;
        return NextResponse.json(data, {status: res.status, headers: relayed});
    } catch (error) {
        console.error(`[proxy] ${path} failed`, error);
        const headers = {"Cache-Control": privateNoStore ? "private, no-store" : errorCacheControl};
        if (problemJsonOnError) {
            return NextResponse.json(
                {type: "about:blank", title: "Bad Gateway", status: 502, detail: errorMessage},
                {status: 502, headers: {...headers, "content-type": "application/problem+json"}},
            );
        }
        return NextResponse.json({error: errorMessage}, {status: 502, headers});
    }
}
