import {NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";

// Picks change once nightly; a 10-minute cache keeps backend load low while
// bounding how long a stale game can show after the nightly cutover.
// Tagged 'round-today' so auth events can invalidate it via revalidateTag.
export const revalidate = 600;

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/today`, {
            next: {revalidate: 600, tags: ['round-today']},
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


