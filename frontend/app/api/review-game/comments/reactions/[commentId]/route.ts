import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN} from "@/lib/backend";
import {rejectUntrustedOrigin} from "@/lib/requestOrigin";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

/**
 * Toggles the authenticated user's reaction to a review-game comment.
 *
 * Requires a trusted Origin/Referer before using the session cookie, matching
 * the comment-archive mutation proxy.
 */
export async function POST(
    req: NextRequest,
    {params}: { params: Promise<{ commentId: string }> },
) {
    const {commentId} = await params;
    const rejected = rejectUntrustedOrigin(req.headers, "comments/reactions", commentId);
    if (rejected) return rejected;
    const token = (await cookies()).get("s5_token")?.value;
    if (!token) {
        console.error("[comments/reactions] Missing authentication", {commentId});
        return NextResponse.json(
            {error: "Unauthorized"},
            {status: 401, headers: NO_STORE},
        );
    }
    try {
        const body = await req.json();
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(commentId)}/reactions`,
            {
                method: "POST",
                headers: {
                    accept: "application/json",
                    "content-type": "application/json",
                    authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(body),
                cache: "no-store",
            },
        );
        const data = await res.json().catch(() => ({}));
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to toggle reaction"},
            {status: 502, headers: NO_STORE},
        );
    }
}
