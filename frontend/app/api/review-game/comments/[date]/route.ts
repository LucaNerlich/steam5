import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN} from "@/lib/backend";

const CACHE_LIVE = {"Cache-Control": "private, max-age=30, must-revalidate"} as const;
const CACHE_HISTORICAL = {"Cache-Control": "private, max-age=31536000, immutable"} as const;
const NO_STORE = {"Cache-Control": "private, no-store"} as const;

function isUtcToday(date: string): boolean {
    try {
        const d = new Date(`${date}T00:00:00Z`);
        if (Number.isNaN(d.getTime())) return false;
        const now = new Date();
        const todayUtc = new Date(Date.UTC(
            now.getUTCFullYear(),
            now.getUTCMonth(),
            now.getUTCDate(),
        ));
        return d.toISOString().slice(0, 10) === todayUtc.toISOString().slice(0, 10);
    } catch {
        return false;
    }
}

/**
 * Retrieves review-game comments for a specified date.
 */
export async function GET(
    _req: NextRequest,
    {params}: { params: Promise<{ date: string }> },
) {
    const {date} = await params;
    const token = (await cookies()).get("s5_token")?.value;
    const headers: Record<string, string> = {accept: "application/json"};
    if (token) {
        headers.authorization = `Bearer ${token}`;
    }
    const today = isUtcToday(date);
    // Authenticated bodies include reactedByViewer — never long-cache them.
    const cacheHeaders = token ? NO_STORE : (today ? CACHE_LIVE : CACHE_HISTORICAL);
    try {
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(date)}`,
            {
                headers,
                ...(token
                    ? {cache: "no-store" as const}
                    : {next: {revalidate: today ? 30 : 31536000}}),
            },
        );
        if (!res.ok) {
            return NextResponse.json(
                {error: "Failed to fetch comments"},
                {status: res.status, headers: NO_STORE},
            );
        }
        const data = await res.json();
        return NextResponse.json(data, {status: res.status, headers: cacheHeaders});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to fetch comments"},
            {status: 502, headers: NO_STORE},
        );
    }
}
