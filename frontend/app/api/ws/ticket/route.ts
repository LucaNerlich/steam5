import {NextRequest, NextResponse} from 'next/server';
import {cookies} from 'next/headers';

const BACKEND_ORIGIN = process.env.API_DOMAIN || process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
const isDevelopment = process.env.NODE_ENV === 'development';

// Per-user, short-lived — must never be cached by Next, a CDN, or a proxy.
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

/**
 * Determines whether an origin uses a loopback hostname.
 *
 * @param origin - The origin URL to inspect
 * @returns `true` if the origin hostname is `localhost`, `127.0.0.1`, or `::1`, `false` otherwise
 */
function isLoopbackOrigin(origin: string): boolean {
    try {
        const {hostname} = new URL(origin);
        return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1';
    } catch {
        return false;
    }
}

/**
 * Determines whether a scope key matches the expected date-based format.
 *
 * @param scopeKey - The scope key to validate
 * @returns `true` if the scope key contains a `YYYY-MM-DD` date with an optional numeric suffix, `false` otherwise.
 */
function isValidScopeKey(scopeKey: string): boolean {
    return /^\d{4}-\d{2}-\d{2}(:\d+:\d+)?$/.test(scopeKey);
}

/**
 * Retrieves a WebSocket ticket for the requested scope.
 *
 * @param request - Request containing the scope key query parameter and authentication cookie
 * @returns A JSON response containing the ticket, or `null` when the scope is invalid, authentication is unavailable, or the backend request fails
 */
export async function GET(request: NextRequest) {
    const scopeKey = request.nextUrl.searchParams.get('scopeKey') ?? '';
    if (!isValidScopeKey(scopeKey)) {
        return NextResponse.json({ticket: null}, {status: 400, headers: NO_STORE});
    }

    const token = (await cookies()).get('s5_token')?.value;
    // Anonymous mode: client connects without a ticket.
    if (!token) return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});

    // Bearer tokens must not cross the network in plaintext — but a loopback
    // backend never leaves the host, so it's exempt from the HTTPS requirement.
    if (!isDevelopment && !isLoopbackOrigin(BACKEND_ORIGIN) && !BACKEND_ORIGIN.startsWith('https://')) {
        console.error('[ws-ticket] Backend origin must use HTTPS in production:', BACKEND_ORIGIN);
        return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
    }
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/ws/ticket`, {
            method: 'POST',
            headers: {
                authorization: `Bearer ${token}`,
                accept: 'application/json',
                'content-type': 'application/json',
            },
            body: JSON.stringify({scopeKey}),
            cache: 'no-store',
            signal: AbortSignal.timeout(5000),
        });
        if (!res.ok) return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
        const data = await res.json();
        return NextResponse.json({ticket: data?.ticket ?? null}, {status: 200, headers: NO_STORE});
    } catch (error) {
        console.error('[ws-ticket] Backend fetch failed:', error);
        return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
    }
}
