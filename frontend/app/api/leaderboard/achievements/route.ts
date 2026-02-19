import { NextResponse } from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

// Cache based on timeframe: shorter for daily, longer for all-time
export const revalidate = 5; // Default 5 seconds

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const timeframe = searchParams.get('timeframe') || 'all';

    const url = `${BACKEND_ORIGIN}/api/stats/users/achievements?timeframe=${encodeURIComponent(timeframe)}`;

    // Use appropriate cache duration based on timeframe
    const cacheTime = timeframe === 'daily' ? 5 : timeframe === 'weekly' ? 10 : 30;

    const res = await fetch(url, {
      headers: { accept: "application/json" },
      next: { revalidate: cacheTime },
    });
    const data = await res.json();
    
    // Pass through server timezone offset header
    const serverOffsetHeader = res.headers.get('X-Server-Timezone-Offset');
    const headers: HeadersInit = {};
    if (serverOffsetHeader) {
      headers['X-Server-Timezone-Offset'] = serverOffsetHeader;
    }
    
    return NextResponse.json(data, { 
      status: res.status,
      headers,
    });
  } catch (error) {
    console.error('[Achievements API] Error:', error);
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 502 });
  }
}
