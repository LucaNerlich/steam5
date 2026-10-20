import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN, BACKEND_TIMEOUT_MS, bearerAuthHeaders, forwardedForHeaders} from "@/lib/backend";


export async function GET(req: NextRequest, {params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {
            status: 401,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
    const auth = bearerAuthHeaders(token);
    if (!auth) {
        return NextResponse.json({error: "Backend origin not allowed"}, {
            status: 503,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/my/day/${encodeURIComponent(date)}`, {
            headers: {"accept": "application/json", ...auth, ...forwardedForHeaders(req)},
            cache: 'no-store',
            signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS)
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

