import {NextRequest} from "next/server";
import {proxyBackendJson} from "@/lib/backend";

export async function GET(request: NextRequest) {
    const requested = (request.nextUrl.searchParams.get('timeframe') || 'all').toLowerCase();
    // The backend's "today" alias means daily; unknown values must not mint unbounded 24h keys.
    const normalized = requested === 'today' ? 'daily' : requested;
    const timeframe = ['daily', 'weekly', 'season', 'monthly', 'all'].includes(normalized) ? normalized : 'all';
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
