import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN} from "@/lib/backend";

const NO_STORE = {"Cache-Control": "private, no-store"} as const;

export async function GET(
    _req: NextRequest,
    {params}: { params: Promise<{ date: string }> },
) {
    const {date} = await params;
    const token = (await cookies()).get("s5_token")?.value;
    const headers: Record<string, string> = {accept: "application/json"};
    if (token) {
        headers.authorization = `Bearer ${token}`;
    }
    try {
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(date)}`,
            {headers, cache: "no-store"},
        );
        if (!res.ok) {
            return NextResponse.json(
                {error: "Failed to fetch comments"},
                {status: res.status, headers: NO_STORE},
            );
        }
        const data = await res.json();
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to fetch comments"},
            {status: 502, headers: NO_STORE},
        );
    }
}

export async function POST(
    req: NextRequest,
    {params}: { params: Promise<{ date: string }> },
) {
    const {date} = await params;
    const token = (await cookies()).get("s5_token")?.value;
    if (!token) {
        return NextResponse.json(
            {error: "Unauthorized"},
            {status: 401, headers: NO_STORE},
        );
    }
    try {
        const body = await req.json();
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/review-game/comments/${encodeURIComponent(date)}`,
            {
                method: "POST",
                headers: {
                    accept: "application/json",
                    "content-type": "application/json",
                    authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(body),
                cache: "no-store",
            },
        );
        const data = await res.json().catch(() => ({}));
        return NextResponse.json(data, {status: res.status, headers: NO_STORE});
    } catch (e) {
        console.error(e);
        return NextResponse.json(
            {error: "Failed to post comment"},
            {status: 502, headers: NO_STORE},
        );
    }
}
