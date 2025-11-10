import { NextResponse } from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const timeframe = searchParams.get('timeframe') || 'all';
    
    const url = `${BACKEND_ORIGIN}/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`;
    
    // Spring Boot already handles caching, so we just pass through with minimal Next.js caching
    const res = await fetch(url, {
      headers: { accept: "application/json" },
      next: { revalidate: 60, tags: [`leaderboard:achievements:${timeframe}`, "leaderboard"] }, // Light caching, Spring handles the heavy lifting
    });
    const data = await res.json();
    
    return NextResponse.json(data, { 
      status: res.status,
      headers: {
        'Cache-Control': 'public, s-maxage=60, max-age=30', // Light client-side cache, Spring handles server-side
      }
    });
  } catch (error) {
    console.error('[Achievements API] Error:', error);
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 502 });
  }
}
