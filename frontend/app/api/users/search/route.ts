import {NextRequest, NextResponse} from "next/server";
import {BACKEND_ORIGIN} from "@/lib/backend";

export async function GET(req: NextRequest) {
    const q = req.nextUrl.searchParams.get("q") ?? "";
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/users/search?q=${encodeURIComponent(q)}`, {
            headers: {"accept": "application/json"},
            cache: "no-store",
        });
        const data = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch {
        return NextResponse.json({error: "Failed to search users"}, {status: 502});
    }
}
