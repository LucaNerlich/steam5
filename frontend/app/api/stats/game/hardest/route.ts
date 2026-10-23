import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/stats/game/hardest",
        revalidate: 3600,
        tags: ["stats-hardest-games"],
        // Relayed so the frontend's "Last updated" line has a timestamp to render.
        passThroughHeaders: ["X-Leaderboard-Refreshed-At"],
        errorMessage: "Failed to load hardest games",
    });
}
