import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    const q = req.nextUrl.searchParams.get("q") ?? "";
    // Per-request route: the caller's IP is relayed so the backend's search limiter buckets
    // real users rather than this server.
    return proxyBackendJson(req, {
        path: `/api/users/search?q=${encodeURIComponent(q)}`,
        errorMessage: "Failed to search users",
    });
}
