import {NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";

// Always proxy today's picks fresh — no ISR layer here. The client fetches this
// with `cache: 'no-store'`, so the round page always reflects the backend's
// current picks. The backend still sends its own short-lived cache headers.
export const dynamic = 'force-dynamic';

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/today`, {
            cache: 'no-store',
            headers: {"accept": "application/json"}
        });
        const data: ReviewGameState = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch daily picks"}, {
            status: 502
        });
    }
}


