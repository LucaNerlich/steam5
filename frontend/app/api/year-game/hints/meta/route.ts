import {NextResponse} from "next/server";
import type {HintMetaResponse} from "@/types/year-game";

export const revalidate = 3600;

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/year-game/hints/meta`, {
            next: {revalidate: 3600},
            headers: {"accept": "application/json"},
        });
        const data: HintMetaResponse = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch hint metadata"}, {status: 502});
    }
}
