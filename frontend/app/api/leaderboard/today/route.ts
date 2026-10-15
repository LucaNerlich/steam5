import {NextRequest, NextResponse} from "next/server";
import {BACKEND_ORIGIN, forwardedForHeaders} from "@/lib/backend";


// Matches the 60-second Caffeine TTL on the backend
export const revalidate = 60;

export async function GET(req: NextRequest) {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/today`, {
            headers: {"accept": "application/json", ...forwardedForHeaders(req)},
            next: { revalidate: 60 },
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? { error: "Failed to load leaderboard" }, { status: res.status });
        }
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load leaderboard" }, { status: 502 });
    }
}


