import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN, forwardedForHeaders} from "@/lib/backend";
import {rejectUntrustedOrigin} from "@/lib/requestOrigin";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

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
    const rejected = rejectUntrustedOrigin(req.headers, "comments/archive", commentId);
    if (rejected) return rejected;
    const token = (await cookies()).get("s5_token")?.value;
    if (!token) {
        console.error("[comments/archive] Missing authentication", {commentId});
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
                    ...forwardedForHeaders(req),
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
