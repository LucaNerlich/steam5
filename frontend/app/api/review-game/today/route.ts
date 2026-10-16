import {NextRequest, NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";
import {BACKEND_ORIGIN, BACKEND_TIMEOUT_MS} from "@/lib/backend";

// Cache today's data briefly at the edge; backend now caches live data for ~30 min.
// Tagged 'round-today' so auth events can invalidate it via revalidateTag.
export const revalidate = 60;


export async function GET(req: NextRequest) {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/today`, {
            next: {revalidate: 60, tags: ['round-today']},
            headers: {"accept": "application/json"},
            signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS)
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? {error: "Failed to fetch daily picks"}, {status: res.status});
        }
        const data: ReviewGameState = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch daily picks"}, {
            status: 502
        });
    }
}


