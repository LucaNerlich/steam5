import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

// Revalidate defaults to the shortest TTL (daily); per-fetch revalidate handles longer ones
export const revalidate = 300;

export async function GET(request: NextRequest) {
    const requested = request.nextUrl.searchParams.get('timeframe') || 'all';
    // Unknown values must not become unbounded 24h cache keys; backend maps them to all-time.
    const timeframe = ['daily', 'weekly', 'season', 'monthly', 'all'].includes(requested) ? requested : 'all';
    // Match backend Caffeine TTLs: 5min daily, 1hr weekly/season/monthly, 24hr all-time
    const cacheTime = timeframe === 'daily'
        ? 300
        : timeframe === 'weekly' || timeframe === 'season' || timeframe === 'monthly' ? 3600 : 86400;

    return proxyBackendJson(request, {
        path: `/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`,
        revalidate: cacheTime,
        errorMessage: "Failed to load achievements",
    });
}
