import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(request: NextRequest) {
    return proxyBackendJson(request, {
        path: "/api/review-game/next-challenge-time",
        revalidate: 60,
        tags: ["next-challenge-time"],
        cacheControl: "public, s-maxage=60, max-age=30",
        errorMessage: "Failed to load next challenge time",
    });
}
