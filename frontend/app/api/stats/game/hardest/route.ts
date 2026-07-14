import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export const revalidate = 3600;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/hardest`, {
            headers: {"accept": "application/json"},
            next: {revalidate, tags: ["stats-hardest-games"]},
        });
        const data = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch {
        return NextResponse.json({error: "Failed to load hardest games"}, {status: 502});
    }
}
