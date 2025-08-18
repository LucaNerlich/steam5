import {NextResponse} from "next/server";

export const revalidate = 0;
export const dynamic = "force-dynamic";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: Request) {
    const {searchParams} = new URL(request.url);
    const limit = searchParams.get('limit') ?? '60';
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/days?limit=${encodeURIComponent(limit)}`, {
            cache: "no-store",
            headers: {"accept": "application/json"}
        });
        const data: string[] = await res.json();
        return NextResponse.json(data, {status: res.status, headers: {"Cache-Control": "no-store"}});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch archive days"}, {status: 502});
    }
}


