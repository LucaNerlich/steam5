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

            const data = await res.json();

            const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
            const headers: HeadersInit = {};
            if (refreshedAtHeader) {
                headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
            }

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
