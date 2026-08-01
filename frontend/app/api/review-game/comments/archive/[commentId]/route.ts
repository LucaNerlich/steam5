import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN} from "@/lib/backend";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

/**
 * Soft-archives a comment via the authenticated moderator session.
 */
export async function POST(
    _req: NextRequest,
    {params}: { params: Promise<{ commentId: string }> },
) {
    const {commentId} = await params;
    const token = (await cookies()).get("s5_token")?.value;
    if (!token) {
        return NextResponse.json(
            {error: "Unauthorized"},
            {status: 401, headers: NO_STORE},
        );
    }
    try {
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(commentId)}/archive`,
            {
                method: "POST",
                headers: {
                    accept: "application/json",
                    authorization: `Bearer ${token}`,
                },
                cache: "no-store",
            },
        );
        const data = await res.json().catch(() => ({}));
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to archive comment"},
            {status: 502, headers: NO_STORE},
        );
    }
}
