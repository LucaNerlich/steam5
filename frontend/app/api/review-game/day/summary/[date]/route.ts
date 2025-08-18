import {NextResponse} from "next/server";

export const revalidate = 0;
export const dynamic = "force-dynamic";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(_req: Request, {params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/day/${encodeURIComponent(date)}`, {
            cache: "no-store",
            headers: {"accept": "application/json"}
        });
        if (!res.ok) return NextResponse.json([], {status: res.status});
        const data = await res.json() as { picks: Array<{ appId: number; name: string }> };
        const picks: Array<{ appId: number; name: string }> = data?.picks ?? [];
        const titles = picks.map((p, i) => ({round: i + 1, appId: p.appId, name: p.name}));
        return NextResponse.json(titles, {status: 200});
    } catch (e) {
        console.error(e);
        return NextResponse.json([], {status: 502});
    }
}


