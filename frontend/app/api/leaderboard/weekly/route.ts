import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Matches the 10-minute Caffeine TTL on the backend
export const revalidate = 600;

export async function GET(req: NextRequest) {
    const floating = req.nextUrl.searchParams.get('floating') === 'true';
    return proxyBackendJson(req, {
        path: `/api/leaderboard/weekly${floating ? '?floating=true' : ''}`,
        revalidate: 600,
        // Only present for floating=true (the MV-backed variant); absent for the live path,
        // which is correct and expected.
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        errorMessage: "Failed to load leaderboard",
    });
}
