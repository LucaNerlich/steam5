import {NextRequest, NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

// Matches the 10-minute Caffeine TTL on the backend
export const revalidate = 600;

export async function GET(req: NextRequest) {
    const floating = req.nextUrl.searchParams.get('floating') === 'true';
    const suffix = floating ? '?floating=true' : '';
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/weekly${suffix}`, {
            headers: {"accept": "application/json"},
            next: { revalidate: 600 },
        });
        const data = await res.json();

        // Pass through the leaderboard freshness header (mirrors the achievements route's
        // X-Server-Timezone-Offset handling) — otherwise NextResponse.json below would silently
        // drop it. Only present for floating=true (the MV-backed variant); absent for the live,
        // non-floating path, which is correct and expected.
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


