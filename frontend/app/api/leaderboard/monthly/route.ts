import {NextRequest, NextResponse} from "next/server";
import {forwardedForHeaders} from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export const revalidate = 600;

export async function GET(req: NextRequest) {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/monthly`, {
            headers: {"accept": "application/json", ...forwardedForHeaders(req)},
            next: { revalidate: 600 },
        });
        const data = await res.json();

        // Pass through the leaderboard freshness header (mirrors the achievements route's
        // X-Server-Timezone-Offset handling) — otherwise NextResponse.json below would silently
        // drop it, and the frontend's "Last updated" line would never have anything to render.
        const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
        const headers: HeadersInit = {};
        if (refreshedAtHeader) {
            headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
        }

        return NextResponse.json(data, { status: res.status, headers });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


