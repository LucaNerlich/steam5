import {NextRequest, NextResponse} from "next/server";
import {proxyCommentMutation} from "@/lib/commentMutation";


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
    // The helper owns the whole gate order — origin, then auth, then the lazy body parse —
    // so an unauthenticated caller cannot force req.json() to buffer anything, and the
    // sanitized missing-auth log lives in one place.
    return proxyCommentMutation(req, {
        route: "comments/reactions",
        path: `/api/review-game/comments/${encodeURIComponent(commentId)}/reactions`,
        commentId,
        parseBody: () => req.json(),
        errorMessage: "Failed to toggle reaction",
    });
}
