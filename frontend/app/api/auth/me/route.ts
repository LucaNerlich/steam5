import {NextRequest, NextResponse} from 'next/server';
import {cookies} from 'next/headers';
import {BACKEND_ORIGIN, bearerAuthHeaders, forwardedForHeaders} from '@/lib/backend';


// Per-user auth state — must never be cached by Next, a CDN, or a proxy.
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export async function GET(req: NextRequest) {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    const auth = bearerAuthHeaders(token);
    if (!auth) {
        // The origin gate is a configuration failure, not a signed-out user.
        console.error('[auth/me] Backend origin is not trusted for a bearer token');
        return NextResponse.json({error: 'backend origin not allowed'}, {status: 503, headers: NO_STORE});
    }
    try {
        // Fix #4: pass the JWT in the Authorization header, not as a URL query param.
        const res = await fetch(`${BACKEND_ORIGIN}/api/auth/validate`, {
            headers: {...auth, ...forwardedForHeaders(req)},
            cache: 'no-store',
            // A hung backend must not stall the route handler indefinitely (same
            // pattern as resolveAuth in the root layout).
            signal: AbortSignal.timeout(3000),
        });
        if (res.status === 401 || res.status === 403) {
            return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
        }
        if (!res.ok) {
            // An indeterminate backend failure must not be presented as a definitive
            // logout: keep the last known auth state in the client instead.
            console.error(`[auth/me] validate failed with ${res.status}`);
            return NextResponse.json({error: 'validate failed'}, {status: 502, headers: NO_STORE});
        }
        const data = await res.json();
        return NextResponse.json({
            signedIn: Boolean(data.valid),
            steamId: data.steamId,
            avatar: data.avatar ?? null,
        }, {status: 200, headers: NO_STORE});
    } catch (e) {
        const timedOut = e instanceof Error && (e.name === 'TimeoutError' || e.name === 'AbortError');
        console.error(`[auth/me] validate ${timedOut ? 'timed out' : 'failed'}`, e);
        return NextResponse.json(
            {error: timedOut ? 'timeout' : 'unavailable'},
            {status: timedOut ? 504 : 502, headers: NO_STORE},
        );
    }
}
