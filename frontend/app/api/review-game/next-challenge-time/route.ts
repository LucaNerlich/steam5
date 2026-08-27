import { NextRequest, NextResponse } from "next/server";
import { forwardedForHeaders } from "@/lib/backend";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export async function GET(request: NextRequest) {
  try {
    const url = `${BACKEND_ORIGIN}/api/review-game/next-challenge-time`;

    const res = await fetch(url, {
      headers: { accept: "application/json", ...forwardedForHeaders(request) },
      next: { revalidate: 60, tags: ["next-challenge-time"] },
    });
    if (!res.ok) {
      // Backend error: pass the payload through with the upstream status,
      // falling back to a generic error body when it isn't JSON.
      const errorBody: unknown = await res.json().catch(() => null);
      return NextResponse.json(
        errorBody ?? { error: "Failed to load next challenge time" },
        {
          status: res.status,
          headers: {
            'Cache-Control': 'public, s-maxage=60, max-age=30',
          }
        }
      );
    }

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

