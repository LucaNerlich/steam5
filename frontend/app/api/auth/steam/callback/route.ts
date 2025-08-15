import {NextRequest, NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: NextRequest) {
    const url = new URL(req.url);
    const qs = url.searchParams.toString();
    const verifyUrl = `${BACKEND_ORIGIN}/api/auth/steam/callback${qs ? `?${qs}` : ''}`;
    try {
        console.log('[steam5] callback start', {requestUrl: url.toString(), verifyUrl});
        const res = await fetch(verifyUrl, {headers: {accept: 'application/json'}});
        if (!res.ok) {
            const base = process.env.NEXT_PUBLIC_DOMAIN || url.origin;
            console.log('[steam5] callback verify failed; redirect base', {base});
            return NextResponse.redirect(new URL(`/review-guesser/1?auth=failed`, base));
        }
        const data = await res.json() as { steamId: string; token: string };
        const base = process.env.NEXT_PUBLIC_DOMAIN || url.origin;
        console.log('[steam5] callback ok; redirect base', {base});
        const resp = NextResponse.redirect(new URL(`/review-guesser/1?auth=ok`, base));
        resp.cookies.set('s5_token', data.token, {
            httpOnly: true,
            sameSite: 'lax',
            secure: base.startsWith('https'),
            path: '/',
            maxAge: 60 * 60 * 24 * 30,
        });
        return resp;
    } catch (e) {
        const base = process.env.NEXT_PUBLIC_DOMAIN || url.origin;
        console.log('[steam5] callback error; redirect base', {
            base,
            error: e instanceof Error ? e.message : String(e)
        });
        return NextResponse.redirect(new URL(`/review-guesser/1?auth=error`, base));
    }
}


