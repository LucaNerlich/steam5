import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Revalidate defaults to the shortest TTL (daily); per-fetch revalidate handles longer ones
export const revalidate = 300;

export async function GET(request: NextRequest) {
    const timeframe = request.nextUrl.searchParams.get('timeframe') || 'all';
    // Match backend Caffeine TTLs: 5min daily, 1hr weekly/season, 24hr all-time
    const cacheTime = timeframe === 'daily' ? 300 : timeframe === 'weekly' || timeframe === 'season' ? 3600 : 86400;

    return proxyBackendJson(request, {
        path: `/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`,
        revalidate: cacheTime,
        passThroughHeaders: ["X-Server-Timezone-Offset"],
        errorMessage: "Failed to load achievements",
    });
}
