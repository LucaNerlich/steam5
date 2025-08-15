import {NextRequest, NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(req: NextRequest) {
    const url = new URL(req.url);
    const qs = url.searchParams.toString();
    const verifyUrl = `${BACKEND_ORIGIN}/api/auth/steam/callback${qs ? `?${qs}` : ''}`;
    try {
        const res = await fetch(verifyUrl, {headers: {accept: 'application/json'}});
        if (!res.ok) {
            return NextResponse.redirect(new URL(`/review-guesser/1?auth=failed`, url.origin));
        }
        const data = await res.json() as { steamId: string; token: string };
        const resp = NextResponse.redirect(new URL(`/review-guesser/1?auth=ok`, url.origin));
        resp.cookies.set('s5_token', data.token, {
            httpOnly: true,
            sameSite: 'lax',
            secure: url.protocol === 'https:',
            path: '/',
            maxAge: 60 * 60 * 24 * 30,
        });
        return resp;
    } catch (e) {
        return NextResponse.redirect(new URL(`/review-guesser/1?auth=error`, url.origin));
    }
}


