import {BACKEND_ORIGIN, forwardedForHeaders} from "@/lib/backend";
import {NextRequest, NextResponse} from "next/server";

export const revalidate = 600;
const FETCH_TIMEOUT_MS = 30_000;

export async function GET(req: NextRequest) {
    try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

        try {
            const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/perfect-days`, {
                headers: {"accept": "application/json", ...forwardedForHeaders(req)},
                next: {revalidate, tags: ["stats-perfect-days"]},
                signal: controller.signal,
            });

            const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
            const headers: HeadersInit = {};
            if (refreshedAtHeader) {
                headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
            }

            if (!res.ok) {
                // Backend error: pass the payload through with the upstream status,
                // falling back to a problem+json error body when it isn't JSON.
                const errorBody: unknown = await res.json().catch(() => null);
                return NextResponse.json(errorBody ?? {
                    type: 'about:blank',
                    title: res.statusText || 'Bad Gateway',
                    status: res.status,
                    detail: 'Failed to load perfect days',
                }, {status: res.status, headers: {...headers, 'content-type': 'application/problem+json'}});
            }

            const data = await res.json();
            return NextResponse.json(data, {status: res.status, headers});
        } finally {
            clearTimeout(timeout);
        }
    } catch (error) {
        console.error('[Perfect Days API] Error:', error);
        return NextResponse.json(
            {
                type: 'about:blank',
                title: 'Bad Gateway',
                status: 502,
                detail: 'Failed to load perfect days',
            },
            {
                status: 502,
                headers: {'content-type': 'application/problem+json'},
            },
        );
    }
}
