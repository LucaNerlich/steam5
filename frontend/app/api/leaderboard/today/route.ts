import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Matches the 60-second Caffeine TTL on the backend
export const revalidate = 60;

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/leaderboard/today",
        revalidate: 60,
        errorMessage: "Failed to load leaderboard",
    });
}
