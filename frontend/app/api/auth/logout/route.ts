import {NextRequest, NextResponse} from 'next/server';

export async function GET(req: NextRequest) {
    const xfHost = req.headers.get('x-forwarded-host');
    const xfProto = req.headers.get('x-forwarded-proto') || 'https';
    const base = (xfHost ? `${xfProto}://${xfHost}` : new URL(req.url).origin);
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
    return resp;
}


