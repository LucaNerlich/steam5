import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export const revalidate = 3600;

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/stats/game/hardest",
        revalidate,
        tags: ["stats-hardest-games"],
        // Relayed so the frontend's "Last updated" line has a timestamp to render.
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        errorMessage: "Failed to load hardest games",
    });
}
