import {BACKEND_ORIGIN} from "@/lib/backend";
import {NextResponse} from "next/server";

export const revalidate = 3600;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/perfect-days`, {
            headers: {"accept": "application/json"},
            next: {revalidate, tags: ["stats-perfect-days"]},
        });
        const data = await res.json();

        const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
        const headers: HeadersInit = {};
        if (refreshedAtHeader) {
            headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
        }

        return NextResponse.json(data, {status: res.status, headers});
    } catch {
        return NextResponse.json({error: "Failed to load perfect days"}, {status: 502});
    }
}
