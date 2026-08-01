import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN} from "@/lib/backend";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

/**
 * Retrieves review-game comments for a specified date.
 *
 * @param params - Route parameters containing the requested date.
 * @returns A response containing the comments, or an error response when retrieval fails.
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
    try {
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(date)}`,
            {headers, cache: "no-store"},
        );
        if (!res.ok) {
            return NextResponse.json(
                {error: "Failed to fetch comments"},
                {status: res.status, headers: NO_STORE},
            );
        }
        const data = await res.json();
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to fetch comments"},
            {status: 502, headers: NO_STORE},
        );
    }
}
