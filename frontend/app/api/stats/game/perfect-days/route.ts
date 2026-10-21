import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export const revalidate = 600;

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/stats/game/perfect-days",
        revalidate,
        tags: ["stats-perfect-days"],
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        timeoutMs: 30_000,
        errorMessage: "Failed to load perfect days",
        problemJsonOnError: true,
    });
}
