import {revalidatePath, revalidateTag} from 'next/cache';
import {NextRequest, NextResponse} from "next/server";
import {forwardedForHeaders} from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

/** Trusted public origin of this site; never derived from client-supplied headers. */
const SITE_ORIGIN = (process.env.NEXT_PUBLIC_DOMAIN || "").replace(/\/$/, "");

/** Resolve the redirect base from configuration only, falling back to the request origin. */
function resolveBase(req: NextRequest): string {
    if (SITE_ORIGIN) return SITE_ORIGIN;
    return new URL(req.url).origin;
}

/** Clear the CSRF state cookie — called on every code path so it never lingers. */
function clearStateCookie(resp: NextResponse, base: string): void {
    resp.cookies.set('s5_state', '', {
        httpOnly: false, // was set by client-side JS; keep attributes consistent
        sameSite: 'lax',
        secure: base.startsWith('https'),
        path: '/',
        maxAge: 0,
    });
}

export async function GET(req: NextRequest) {
    const url = new URL(req.url);
    const base = resolveBase(req);

    // Fix #6: verify the CSRF state token. SteamLoginButton stores a random UUID in
    // s5_state (max-age 300s) and appends it to the login URL. The backend embeds it
    // in openid.return_to; Steam carries it back here as a plain query param. Both the
    // cookie and the URL param MUST be present and match — a missing cookie (expired
    // mid-flow or cleared) is treated as a mismatch, otherwise the check would be
    // silently skipped exactly when it matters (login-CSRF protection).
    const stateFromUrl = url.searchParams.get('state');
    const stateFromCookie = req.cookies.get('s5_state')?.value;
    if (!stateFromCookie || !stateFromUrl || stateFromUrl !== stateFromCookie) {
        console.error('[steam5] CSRF state mismatch — possible login-CSRF attack', {
            hasUrlState: Boolean(stateFromUrl),
            hasCookieState: Boolean(stateFromCookie),
        });
        const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=csrf_error', base));
        clearStateCookie(resp, base);
        return resp;
    }

    const qs = url.searchParams.toString();
    const verifyUrl = `${BACKEND_ORIGIN}/api/auth/steam/callback${qs ? `?${qs}` : ''}`;

    try {
        // The backend verifies the OpenID ticket against Steam's API, so this is
        // an upstream HTTP roundtrip; bound it with a generous timeout so a hung
        // backend or Steam outage degrades to the auth=error redirect instead of
        // stalling the route handler indefinitely.
        const res = await fetch(verifyUrl, {headers: {accept: 'application/json', ...forwardedForHeaders(req)}, signal: AbortSignal.timeout(10000)});
        if (!res.ok) {
            console.log('[steam5] callback verify failed', {base, status: res.status});
            const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=failed', base));
            clearStateCookie(resp, base);
            return resp;
        }

        const data = await res.json() as {steamId?: string; token?: string};
        if (!data.token || !data.steamId) {
            console.error('[steam5] callback: backend returned unexpected response shape', {hasSteamId: Boolean(data.steamId), hasToken: Boolean(data.token)});
            const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=error', base));
            clearStateCookie(resp, base);
            return resp;
        }
        const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=ok', base));
        resp.cookies.set('s5_token', data.token, {
            httpOnly: true,
            sameSite: 'lax',
            secure: base.startsWith('https'),
            path: '/',
            maxAge: 60 * 60 * 24 * 30,
        });
        clearStateCookie(resp, base);
        // Invalidate the round page tree and the tagged today-fetches (page +
        // proxy route) so the freshly-authenticated user sees current data.
        revalidatePath('/review-guesser', 'layout');
        revalidateTag('round-today', 'max');
        return resp;
    } catch (e) {
        console.error('[steam5] callback error', {
            base,
            error: e instanceof Error ? e.message : String(e),
        });
        const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=error', base));
        clearStateCookie(resp, base);
        return resp;
    }
}
