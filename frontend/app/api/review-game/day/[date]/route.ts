import {NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";

export const revalidate = 0;
export const dynamic = "force-dynamic";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(_req: Request, {params}: { params: Promise<{ date: string }> }) {
    try {
        const {date} = await params;
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/day/${encodeURIComponent(date)}`, {
            cache: "no-store",
            headers: {"accept": "application/json"}
        });
        if (!res.ok) {
            return NextResponse.json({error: `Failed to fetch archive for ${date}`}, {
                status: res.status,
                headers: {"Cache-Control": "no-store"}
            });
        }
        const data: ReviewGameState = await res.json();
        return NextResponse.json(data, {status: res.status, headers: {"Cache-Control": "no-store"}});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch archived picks"}, {
            status: 502,
            headers: {"Cache-Control": "no-store"}
        });
    }
}


