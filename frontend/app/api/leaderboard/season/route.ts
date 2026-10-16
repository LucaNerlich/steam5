import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Matches the 10-minute Caffeine TTL on the backend
export const revalidate = 600;

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/leaderboard/season",
        revalidate: 600,
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        errorMessage: "Failed to load leaderboard",
    });
}
