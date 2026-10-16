import {revalidatePath, revalidateTag} from 'next/cache';
import {NextRequest, NextResponse} from 'next/server';
import {allowedBackendOrigin, rejectUntrustedOrigin} from '@/lib/requestOrigin';

/** Trusted public origin of this site; never derived from client-supplied headers. */
const SITE_ORIGIN = (process.env.NEXT_PUBLIC_DOMAIN || "").replace(/\/$/, "");

/**
 * Kept as the route's name for the shared gate so existing callers/tests read the same; the
 * policy itself lives in `@/lib/requestOrigin`.
 */
export function allowedLogoutBackendOrigin(
    configuredOrigin: string,
    production = process.env.NODE_ENV === 'production',
    trustedOrigins = process.env.ALLOWED_BACKEND_ORIGINS || '',
): string | null {
    return allowedBackendOrigin(configuredOrigin, production, trustedOrigins);
}

// POST (not GET): SameSite=Lax cookies are not sent on cross-site POSTs, so a
// third-party page cannot force-logout the user via an <img>/GET request.
export async function POST(req: NextRequest) {
    const base = SITE_ORIGIN || new URL(req.url).origin;


    // Reject cross-site submissions before touching the session. The POST-only shape and
    // SameSite=Lax stop the cookie from being *sent*, but this handler cleared it anyway — the
    // response's Set-Cookie is applied by the browser regardless — so any page could force a
    // logout with a cross-site form POST. The sibling mutating proxies already do this.
    const rejected = rejectUntrustedOrigin(req.headers, 'auth/logout', '');
    if (rejected) return rejected;

    // Invalidate the token server-side before clearing the client's copy, so a
    // previously retained/stolen token stops working immediately instead of
    // remaining valid for its full lifetime. Best-effort: logout must still
    // succeed for the browser even if the backend call fails.
    const token = req.cookies.get('s5_token')?.value;
    if (token) {
        const configuredBackend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        const backendOrigin = allowedLogoutBackendOrigin(configuredBackend);
        if (!backendOrigin) {
            console.error('Backend logout call skipped: production backend origin must use HTTPS or be explicitly trusted');
        } else {
            try {
                await fetch(`${backendOrigin}/api/auth/logout`, {
                    method: 'POST',
                    headers: {authorization: `Bearer ${token}`},
                    cache: 'no-store',
                    signal: AbortSignal.timeout(3000),
                });
            } catch (e) {
                console.error('Backend logout call failed', e);
            }
        }
    }

    const resp = NextResponse.redirect(new URL('/review-guesser/1', base));

    // Clear-Site-Data instructs the browser to sweep all cookies for this origin in
    // one shot — more thorough than manually expiring individual cookies.
    // Supported by Chrome and Firefox; Safari ignores it, so we keep the explicit
    // maxAge: 0 below as a fallback for all browsers.
    resp.headers.set('Clear-Site-Data', '"cookies"');

    resp.cookies.set('s5_token', '', {
        httpOnly: true,
        sameSite: 'lax',
        secure: base.startsWith('https'),
        path: '/',
        maxAge: 0,
    });
    // Invalidate the round page tree and the tagged today-fetches (page + proxy
    // route) so the logged-out user no longer sees their prefilled guesses.
    revalidatePath('/review-guesser', 'layout');
    revalidateTag('round-today', 'max');
    return resp;
}

