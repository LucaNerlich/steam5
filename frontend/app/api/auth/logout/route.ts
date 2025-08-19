import {NextRequest, NextResponse} from 'next/server';

export async function GET(req: NextRequest) {
    const xfHost = req.headers.get('x-forwarded-host');
    const xfProto = req.headers.get('x-forwarded-proto') || 'https';
    const base = (xfHost ? `${xfProto}://${xfHost}` : new URL(req.url).origin);
    const resp = NextResponse.redirect(new URL('/review-guesser/1', base));
    resp.cookies.set('s5_token', '', {
        httpOnly: true,
        sameSite: 'lax',
        secure: base.startsWith('https'),
        path: '/',
        maxAge: 0,
    });
    return resp;
}


