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
    const xff = req.headers.get("x-forwarded-for");
    return xff ? {"x-forwarded-for": xff} : {};
}
