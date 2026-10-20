import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Cache today's data briefly at the edge; backend now caches live data for ~30 min.
// Tagged 'round-today' so auth events can invalidate it via revalidateTag.
export const revalidate = 60;

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/review-game/today",
        revalidate: 60,
        tags: ["round-today"],
        errorMessage: "Failed to fetch daily picks",
    });
}
