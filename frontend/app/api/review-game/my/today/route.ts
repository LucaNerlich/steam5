import {NextRequest, NextResponse} from "next/server";
import {cookies} from "next/headers";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json([], {status: 200, headers: {"Cache-Control": "private, no-store"}});
    return proxyBackendJson(req, {
        path: "/api/review-game/my/today",
        bearerToken: token,
        privateNoStore: true,
        errorMessage: "Failed to load today's guesses",
    });
}
