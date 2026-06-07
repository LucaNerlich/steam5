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
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


