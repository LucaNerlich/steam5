import {NextResponse} from "next/server";
import {cookies} from "next/headers";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(_req: Request, {params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {
            status: 401,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/my/day/${encodeURIComponent(date)}`, {
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

