import type {NextRequest} from "next/server";

export const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

/**
 * Forwards the original client IP so backend per-IP rate limiting (which relies on
 * server.forward-headers-strategy=framework) sees the real caller instead of this
 * Next server's own outbound connection IP.
 */
export function forwardedForHeaders(req: NextRequest): Record<string, string> {
    const xff = req.headers.get("x-forwarded-for");
    return xff ? {"x-forwarded-for": xff} : {};
}
