import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    const {searchParams} = new URL(req.url);
    const from = searchParams.get('from') || '';
    const to = searchParams.get('to') || '';

    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return NextResponse.json({error: "Unauthorized"}, {
            status: 401,
            headers: {"Cache-Control": "private, no-store"}
        });
    }

    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    const query = params.toString();

    return proxyBackendJson(req, {
        path: `/api/review-game/my/history${query ? '?' + query : ''}`,
        bearerToken: token,
        privateNoStore: true,
        errorMessage: "Failed to fetch",
    });
}
