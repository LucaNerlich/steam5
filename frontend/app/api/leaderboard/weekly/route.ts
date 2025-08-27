import {NextRequest, NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: NextRequest) {
    const floating = req.nextUrl.searchParams.get('floating') === 'true';
    const suffix = floating ? '?floating=true' : '';
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/weekly${suffix}`, {
            headers: {"accept": "application/json"},
            next: { revalidate: 30, tags: [floating ? "leaderboard:weekly:floating" : "leaderboard:weekly", "leaderboard"] },
        });
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


