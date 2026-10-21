import {NextRequest, NextResponse} from 'next/server';
import {cookies} from 'next/headers';
import {BACKEND_ORIGIN, bearerAuthHeaders, forwardedForHeaders} from '@/lib/backend';


// Per-user auth state — must never be cached by Next, a CDN, or a proxy.
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export async function GET(req: NextRequest) {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    const auth = bearerAuthHeaders(token);
    if (!auth) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    try {
        // Fix #4: pass the JWT in the Authorization header, not as a URL query param.
        const res = await fetch(`${BACKEND_ORIGIN}/api/auth/validate`, {
            headers: {...auth, ...forwardedForHeaders(req)},
            cache: 'no-store',
            // A hung backend must not stall the route handler indefinitely (same
            // pattern as resolveAuth in the root layout).
            signal: AbortSignal.timeout(3000),
        });
        if (!res.ok) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
        const data = await res.json();
        return NextResponse.json({
            signedIn: Boolean(data.valid),
            steamId: data.steamId,
            avatar: data.avatar ?? null,
        }, {status: 200, headers: NO_STORE});
    } catch {
        return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    }
}


