import {NextRequest, NextResponse} from "next/server";
import type {GuessRequest, GuessResponse} from "@/types/review-game";
import {forwardedForHeaders} from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function POST(req: NextRequest) {
    try {
        const body: GuessRequest = await req.json();
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/guess`, {
            method: "POST",
            headers: {"content-type": "application/json", "accept": "application/json", ...forwardedForHeaders(req)},
            body: JSON.stringify(body)
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? {error: "Failed to submit guess"}, {status: res.status});
        }
        const data: GuessResponse = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to submit guess"}, {status: 502});
    }
}


