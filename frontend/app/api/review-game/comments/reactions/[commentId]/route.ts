import {NextRequest, NextResponse} from "next/server";
import {proxyCommentMutation} from "@/lib/commentMutation";

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
    let body: unknown;
    try {
        body = await req.json();
    } catch {
        // A malformed client body is a client error: parsing it inside the gateway try made the
        // 502 branch answer for it, so clients showed a server-failure message and 502 counters
        // logged phantom backend errors.
        return NextResponse.json({error: "bad_request"}, {status: 400, headers: NO_STORE});
    }
    return proxyCommentMutation(req, {
        route: "comments/reactions",
        path: `/api/review-game/comments/${encodeURIComponent(commentId)}/reactions`,
        commentId,
        body,
        errorMessage: "Failed to toggle reaction",
    });
}
