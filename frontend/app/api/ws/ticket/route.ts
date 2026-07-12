import {NextResponse} from 'next/server';
import {cookies} from 'next/headers';

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

// Per-user, short-lived — must never be cached by Next, a CDN, or a proxy.
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export async function GET() {
    const token = (await cookies()).get('s5_token')?.value;
    // Anonymous mode: client connects without a ticket.
    if (!token) return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/ws/ticket`, {
            method: 'POST',
            headers: {authorization: `Bearer ${token}`, accept: 'application/json'},
            cache: 'no-store',
        });
        if (!res.ok) return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
        const data = await res.json();
        return NextResponse.json({ticket: data?.ticket ?? null}, {status: 200, headers: NO_STORE});
    } catch {
        return NextResponse.json({ticket: null}, {status: 200, headers: NO_STORE});
    }
}
