import {NextRequest, NextResponse} from "next/server";
import {BACKEND_ORIGIN, forwardedForHeaders} from "@/lib/backend";

export async function GET(req: NextRequest) {
    const q = req.nextUrl.searchParams.get("q") ?? "";
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/users/search?q=${encodeURIComponent(q)}`, {
            headers: {"accept": "application/json", ...forwardedForHeaders(req)},
            cache: "no-store",
        });
        if (!res.ok) {
            // Backend error: pass the payload through with the upstream status,
            // falling back to a generic error body when it isn't JSON.
            const errorBody: unknown = await res.json().catch(() => null);
            return NextResponse.json(errorBody ?? {error: "Failed to search users"}, {status: res.status});
        }
        const data = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch {
        return NextResponse.json({error: "Failed to search users"}, {status: 502});
    }
}
