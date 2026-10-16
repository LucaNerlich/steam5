import { NextRequest, NextResponse } from "next/server";
import {BACKEND_ORIGIN, BACKEND_TIMEOUT_MS} from "@/lib/backend";


export async function GET(request: NextRequest) {
  try {
    const url = `${BACKEND_ORIGIN}/api/review-game/next-challenge-time`;

    // Shared-cached: the caller's IP must not reach the fetch cache key.
    const res = await fetch(url, {
      headers: { accept: "application/json" },
      next: { revalidate: 60, tags: ["next-challenge-time"] },
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    if (!res.ok) {
      // Backend error: pass the payload through with the upstream status,
      // falling back to a generic error body when it isn't JSON.
      const errorBody: unknown = await res.json().catch(() => null);
      // A non-ok upstream is not shareable: the public TTL belongs on the success branch only.
      return NextResponse.json(
        errorBody ?? { error: "Failed to load next challenge time" },
        {
          status: res.status,
          headers: {
            'Cache-Control': 'no-store',
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

