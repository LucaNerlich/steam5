import {NextRequest, NextResponse} from "next/server";
import {forwardedForHeaders} from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: NextRequest, context: { params: Promise<{ steamId: string }> }) {
    const { steamId } = await context.params;
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/profile/${encodeURIComponent(steamId)}`, {
            headers: {"accept": "application/json", ...forwardedForHeaders(req)},
            next: { revalidate: 300, tags: [`profile:${steamId}`] },
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? { error: "Failed to load profile" }, { status: res.status });
        }
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load profile" }, { status: 502 });
    }
}


