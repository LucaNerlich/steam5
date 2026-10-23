import {NextRequest} from "next/server";
import {proxyCommentMutation} from "@/lib/commentMutation";

/**
 * Soft-archives a comment via the authenticated moderator session.
 *
 * Cookie auth alone is not enough for this mutating route: require a trusted
 * Origin/Referer so cross-site pages cannot trigger archive via the session cookie.
 * (s5_token stays SameSite=Lax for Steam OAuth return compatibility.)
 */
export async function POST(
    req: NextRequest,
    {params}: { params: Promise<{ commentId: string }> },
) {
    const {commentId} = await params;
    return proxyCommentMutation(req, {
        route: "comments/archive",
        path: `/api/review-game/comments/${encodeURIComponent(commentId)}/archive`,
        commentId,
        errorMessage: "Failed to archive comment",
    });
}
