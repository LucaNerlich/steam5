import {NextResponse} from "next/server";
import {cookies} from "next/headers";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET() {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json([], {status: 200, headers: {"Cache-Control": "private, no-store"}});
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/my/today`, {
            headers: {"accept": "application/json", "authorization": `Bearer ${token}`},
            cache: 'no-store'
        });
        const data = await res.json();
        return NextResponse.json(data, {status: res.status, headers: {"Cache-Control": "private, no-store"}});
    } catch (e) {
        console.error(e);
        return NextResponse.json([], {status: 200, headers: {"Cache-Control": "private, no-store"}});
    }
}


