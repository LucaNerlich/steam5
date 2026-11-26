import {cookies} from "next/headers";
import {NextRequest, NextResponse} from "next/server";
import {BACKEND_ORIGIN, BACKEND_TIMEOUT_MS, bearerAuthHeaders, forwardedForHeaders} from "@/lib/backend";
import {rejectUntrustedOrigin, sanitizeForLog} from "@/lib/requestOrigin";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export interface CommentMutationOptions {
    /** Log/error label and origin-check route name, e.g. "comments/reactions". */
    route: string;
    /** Backend path including the encoded comment id. */
    path: string;
    commentId: string;
    errorMessage: string;
    /** JSON body; omit for a body-less POST. */
    body?: unknown;
    /**
     * Lazy body reader for routes whose request body must not be consumed before the origin
     * and auth gates run. Returning a rejected promise is answered with 400.
     */
    parseBody?: () => Promise<unknown>;
}

/**
 * Shared cookie-auth proxy for comment mutations: trusted-origin check, 401, bearer
 * gate, POST, status/body passthrough and one 502 mapping. Lives outside backend.ts
 * because it reads cookies and must not be pulled into a client bundle.
 */
export async function proxyCommentMutation(req: NextRequest, options: CommentMutationOptions) {
    const rejected = rejectUntrustedOrigin(req.headers, options.route, options.commentId);
    if (rejected) return rejected;
    const token = (await cookies()).get("s5_token")?.value;
    if (!token) {
        console.error(`[${options.route}] Missing authentication`, {commentId: sanitizeForLog(options.commentId, 64)});
        return NextResponse.json({error: "Unauthorized"}, {status: 401, headers: NO_STORE});
    }
    const auth = bearerAuthHeaders(token);
    if (!auth) return NextResponse.json({error: "Backend origin not allowed"}, {status: 503, headers: NO_STORE});
    let body = options.body;
    if (options.parseBody) {
        try {
            body = await options.parseBody();
        } catch {
            return NextResponse.json({error: "bad_request"}, {status: 400, headers: NO_STORE});
        }
    }
    try {
        const res = await fetch(`${BACKEND_ORIGIN}${options.path}`, {
            method: "POST",
            headers: {
                accept: "application/json",
                ...(body === undefined ? {} : {"content-type": "application/json"}),
                ...auth,
                ...forwardedForHeaders(req),
            },
            body: body === undefined ? undefined : JSON.stringify(body),
            cache: "no-store",
            signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // tolerating a non-JSON (e.g. empty) error body.
            const errorBody: unknown = await res.json().catch(() => ({}));
            return NextResponse.json(errorBody, {status: res.status, headers: NO_STORE});
        }
        const data = await res.json().catch(() => ({}));
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: options.errorMessage}, {status: 502, headers: NO_STORE});
    }
}
