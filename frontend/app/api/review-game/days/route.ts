import {NextResponse} from "next/server";

const ONE_DAY_REVALIDATE_SECONDS = 86400;
const DEFAULT_LIMIT = 120;
const MAX_LIMIT = 5000;

export const revalidate = 86400;

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: Request) {
    const {searchParams} = new URL(request.url);
    const limitRaw = searchParams.get("limit");
    const parsedLimit = Number.parseInt(limitRaw ?? "", 10);
    const safeLimit = Number.isFinite(parsedLimit) && parsedLimit > 0
        ? Math.min(parsedLimit, MAX_LIMIT)
        : DEFAULT_LIMIT;
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/days?limit=${safeLimit}`, {
            next: {revalidate: ONE_DAY_REVALIDATE_SECONDS},
            headers: {"accept": "application/json"}
        });
        const data: string[] = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch (e) {
        console.error(e);
        return NextResponse.json({error: "Failed to fetch archive days"}, {status: 502});
    }
}


