import { NextResponse } from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: Request) {
  try {
    const url = `${BACKEND_ORIGIN}/api/review-game/next-challenge-time`;
    
    const res = await fetch(url, {
      headers: { accept: "application/json" },
      next: { revalidate: 60, tags: ["next-challenge-time"] },
    });
    const data = await res.json();
    
    return NextResponse.json(data, { 
      status: res.status,
      headers: {
        'Cache-Control': 'public, s-maxage=60, max-age=30',
      }
    });
  } catch (error) {
    console.error('[Next Challenge Time API] Error:', error);
    return NextResponse.json({ error: "Failed to load next challenge time" }, { status: 502 });
  }
}

