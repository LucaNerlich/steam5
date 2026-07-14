import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import type {HintRequest, HintResponse} from "@/types/year-game";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function POST(req: NextRequest) {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {status: 401});
    }

    try {
        const body: HintRequest = await req.json();
        const res = await fetch(`${BACKEND_ORIGIN}/api/year-game/hint`, {
            method: "POST",
            headers: {
                "content-type": "application/json",
                "accept": "application/json",
                "authorization": `Bearer ${token}`,
            },
            body: JSON.stringify(body),
            cache: 'no-store',
        });
        const data: HintResponse = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to reveal hint"}, {status: 502});
    }
}
