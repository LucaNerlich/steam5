import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

// Matches the 60-second Caffeine TTL on the backend
export const revalidate = 60;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/today`, {
            headers: {"accept": "application/json"},
            next: { revalidate: 60 },
        });
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


