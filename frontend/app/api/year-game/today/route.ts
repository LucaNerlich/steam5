import {NextResponse} from "next/server";
import type {YearGameState} from "@/types/year-game";

export const revalidate = 60;

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/year-game/today`, {
            next: {revalidate: 60, tags: ['year-round-today']},
            headers: {"accept": "application/json"},
        });
        const data: YearGameState = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch daily picks"}, {status: 502});
    }
}
