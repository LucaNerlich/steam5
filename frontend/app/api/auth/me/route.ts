import {NextResponse} from 'next/server';
import {cookies} from 'next/headers';

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

// Per-user auth state — must never be cached by Next, a CDN, or a proxy.
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export async function GET() {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    try {
        // Fix #4: pass the JWT in the Authorization header, not as a URL query param.
        const res = await fetch(`${BACKEND_ORIGIN}/api/auth/validate`, {
            headers: {authorization: `Bearer ${token}`},
            cache: 'no-store',
        });
        if (!res.ok) return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
        const data = await res.json();
        return NextResponse.json({signedIn: Boolean(data.valid), steamId: data.steamId}, {status: 200, headers: NO_STORE});
    } catch {
        return NextResponse.json({signedIn: false}, {status: 200, headers: NO_STORE});
    }
}


