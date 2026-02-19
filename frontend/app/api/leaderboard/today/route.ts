import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

// Cache for 5 seconds to reduce backend load while maintaining real-time feel
export const revalidate = 5;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/today`, {
            headers: {"accept": "application/json"},
            next: { revalidate: 5 }, // 5 second cache
        });
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


