import {NextRequest, NextResponse} from "next/server";
import type {BucketsResponse} from "@/types/review-game";
import {forwardedForHeaders} from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: NextRequest) {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/buckets`, {
            next: {revalidate: 60},
            headers: {"accept": "application/json", ...forwardedForHeaders(req)}
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? {error: "Failed to fetch buckets"}, {status: res.status});
        }
        const data: BucketsResponse = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch buckets"}, {status: 502});
    }
}


