import {NextResponse} from 'next/server';

export async function GET() {
    const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
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


