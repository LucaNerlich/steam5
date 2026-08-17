import {revalidatePath, revalidateTag} from 'next/cache';
import {NextRequest, NextResponse} from 'next/server';

/** Trusted public origin of this site; never derived from client-supplied headers. */
const SITE_ORIGIN = (process.env.NEXT_PUBLIC_DOMAIN || "").replace(/\/$/, "");

// POST (not GET): SameSite=Lax cookies are not sent on cross-site POSTs, so a
// third-party page cannot force-logout the user via an <img>/GET request.
export async function POST(req: NextRequest) {
    const base = SITE_ORIGIN || new URL(req.url).origin;
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


