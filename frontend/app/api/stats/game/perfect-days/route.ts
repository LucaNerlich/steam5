import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/stats/game/perfect-days",
        revalidate: 600,
        tags: ["stats-perfect-days"],
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        timeoutMs: 30_000,
        errorMessage: "Failed to load perfect days",
        problemJsonOnError: true,
    });
}
