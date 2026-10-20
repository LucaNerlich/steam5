import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/stats/game?topGamesLimit=10",
        revalidate: 600,
        tags: ["stats:game"],
        cacheControl: "public, s-maxage=600, max-age=600",
        errorMessage: "Failed to load game statistics",
    });
}
