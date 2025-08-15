import {NextResponse} from 'next/server';
import {cookies} from 'next/headers';

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

export async function GET() {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json({signedIn: false}, {status: 200});
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/auth/validate?token=${encodeURIComponent(token)}`, {cache: 'no-store'});
        if (!res.ok) return NextResponse.json({signedIn: false}, {status: 200});
        const data = await res.json();
        return NextResponse.json({signedIn: Boolean(data.valid), steamId: data.steamId}, {status: 200});
    } catch {
        return NextResponse.json({signedIn: false}, {status: 200});
    }
}


