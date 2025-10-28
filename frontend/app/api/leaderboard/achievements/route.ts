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
      next: { revalidate, tags: [`leaderboard:achievements:${timeframe}`, "leaderboard"] },
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 502 });
  }
}
