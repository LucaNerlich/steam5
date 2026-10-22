import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(req: NextRequest) {
    return proxyBackendJson(req, {
        path: "/api/review-game/today",
        revalidate: 60,
        tags: ["round-today"],
        errorMessage: "Failed to fetch daily picks",
    });
}
