import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest, {params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {
            status: 401,
            headers: {"Cache-Control": "private, no-store"}
        });
    }
    return proxyBackendJson(req, {
        path: `/api/review-game/my/day/${encodeURIComponent(date)}`,
        bearerToken: token,
        privateNoStore: true,
        errorMessage: "Failed to fetch",
    });
}
