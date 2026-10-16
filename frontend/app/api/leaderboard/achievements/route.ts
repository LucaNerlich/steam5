import { NextRequest, NextResponse } from "next/server";
import {BACKEND_ORIGIN, forwardedForHeaders} from "@/lib/backend";


// Revalidate defaults to the shortest TTL (daily); per-fetch revalidate handles longer ones
export const revalidate = 300;

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const timeframe = searchParams.get('timeframe') || 'all';

    const url = `${BACKEND_ORIGIN}/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`;

    // Match backend Caffeine TTLs: 5min daily, 1hr weekly/season, 24hr all-time
    const cacheTime = timeframe === 'daily' ? 300 : timeframe === 'weekly' || timeframe === 'season' ? 3600 : 86400;

    const res = await fetch(url, {
      headers: { accept: "application/json", ...forwardedForHeaders(request) },
      next: { revalidate: cacheTime },
    });
    if (!res.ok) {
      // Backend error: pass the payload through with the upstream status,
      // falling back to a generic error body when it isn't JSON.
      const errorBody: unknown = await res.json().catch(() => null);
      return NextResponse.json(
        errorBody ?? { error: "Failed to load achievements" },
        { status: res.status },
      );
    }
    const data = await res.json();

    return NextResponse.json(data, {
      status: res.status,
    });
  } catch (error) {
    console.error('[Achievements API] Error:', error);
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 502 });
  }
}
