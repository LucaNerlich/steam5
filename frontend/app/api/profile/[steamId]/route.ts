import {NextRequest, NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(_req: NextRequest, context: { params: Promise<{ steamId: string }> }) {
    const { steamId } = await context.params;
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/profile/${encodeURIComponent(steamId)}`, {
            headers: {"accept": "application/json"},
            next: { revalidate: 300, tags: [`profile:${steamId}`] },
        });
        const data = await res.json();
        return NextResponse.json(data, { status: res.status });
    } catch {
        return NextResponse.json({ error: "Failed to load profile" }, { status: 502 });
    }
}


