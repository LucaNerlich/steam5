import { NextResponse } from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const timeframe = searchParams.get('timeframe') || 'all';
    
    // Determine revalidation time based on timeframe
    const revalidate = timeframe === 'daily' || timeframe === 'today' ? 300 :  // 5 minutes
                       timeframe === 'weekly' ? 3600 :  // 1 hour
                       86400;  // 1 day for all-time
    
    const url = `${BACKEND_ORIGIN}/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`;
    
    const res = await fetch(url, {
      headers: { accept: "application/json" },
      next: { revalidate: 0, tags: [`leaderboard:achievements:${timeframe}`, "leaderboard"] }, // Temporarily disable cache
    });
    const data = await res.json();
    
    // Add cache control headers to the response
    const cacheControl = timeframe === 'daily' || timeframe === 'today' 
      ? 'public, s-maxage=300, max-age=60, stale-while-revalidate=600'
      : timeframe === 'weekly'
      ? 'public, s-maxage=3600, max-age=600, stale-while-revalidate=7200'
      : 'public, s-maxage=86400, max-age=3600, stale-while-revalidate=172800';
    
    return NextResponse.json(data, { 
      status: res.status,
      headers: {
        'Cache-Control': cacheControl,
      }
    });
  } catch (error) {
    console.error('[Achievements API] Error:', error);
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 502 });
  }
}
