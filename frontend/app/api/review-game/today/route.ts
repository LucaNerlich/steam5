import {NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/today`, {
            // Leverage backend caching; also allow ISR at 60s
            next: {revalidate: 60},
            headers: {"accept": "application/json"}
        });
        const data: ReviewGameState = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch daily picks"}, {status: 502});
    }
}


