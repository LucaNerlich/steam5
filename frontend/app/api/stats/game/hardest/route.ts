import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export const revalidate = 3600;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/hardest`, {
            headers: {"accept": "application/json"},
            next: {revalidate, tags: ["stats-hardest-games"]},
        });
        const data = await res.json();

        // Pass through the leaderboard freshness header (mirrors the achievements/leaderboard
        // routes' pattern) — otherwise NextResponse.json below would silently drop it, and the
        // frontend's "Last updated" line would never have anything to render.
        const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
        const headers: HeadersInit = {};
        if (refreshedAtHeader) {
            headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
        }

        return NextResponse.json(data, {status: res.status, headers});
    } catch {
        return NextResponse.json({error: "Failed to load hardest games"}, {status: 502});
    }
}
