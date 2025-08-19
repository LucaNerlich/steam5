import {NextResponse} from "next/server";
import type {ReviewGameState} from "@/types/review-game";

// Allow ISR: archive days are immutable, today should revalidate briefly
export const revalidate = 60;

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(_req: Request, {params}: { params: Promise<{ date: string }> }) {
    try {
        const {date} = await params;
        const isToday = (() => {
            try {
                const d = new Date(date + 'T00:00:00Z');
                const now = new Date();
                return d.toISOString().slice(0, 10) === new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())).toISOString().slice(0, 10);
            } catch {
                return false;
            }
        })();
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/day/${encodeURIComponent(date)}`, {
            next: {revalidate: isToday ? 60 : 31536000},
            headers: {"accept": "application/json"}
        });
        if (!res.ok) {
            return NextResponse.json({error: `Failed to fetch archive for ${date}`}, {
                status: res.status
            });
        }
        const data: ReviewGameState = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch archived picks"}, {
            status: 502
        });
    }
}


