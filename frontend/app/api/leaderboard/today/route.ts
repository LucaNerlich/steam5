import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/leaderboard/today",
        revalidate: 60,
        errorMessage: "Failed to load leaderboard",
    });
}
