import {NextResponse} from "next/server";
import {cookies} from "next/headers";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: Request) {
    const {searchParams} = new URL(req.url);
    const from = searchParams.get('from') || '';
    const to = searchParams.get('to') || '';

    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {
            status: 401,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
    try {
        const params = new URLSearchParams();
        if (from) params.set('from', from);
        if (to) params.set('to', to);
        const url = `${BACKEND_ORIGIN}/api/review-game/my/history${params.toString() ? '?' + params.toString() : ''}`;

        const res = await fetch(url, {
            headers: {"accept": "application/json", "authorization": `Bearer ${token}`},
            cache: 'no-store'
        });
        if (!res.ok) {
            return NextResponse.json({error: "Failed to fetch"}, {
                status: res.status,
                headers: {"Cache-Control": "private, no-store"}
            });
        }
        const data = await res.json();
        return NextResponse.json(data, {
            status: res.status,
            headers: {"Cache-Control": "private, no-store"}
        });
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch"}, {
            status: 502,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
}

