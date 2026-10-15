import type {NextRequest} from "next/server";

export const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

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
