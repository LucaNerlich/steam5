/**
 * Shared server-side fetch utilities for leaderboard pages.
 * Used in Next.js server components to pre-fetch data with ISR caching.
 */

export const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

/** Valid timeframe values for the achievements API */
export type AchievementTimeframe = "daily" | "weekly" | "season" | "all";

/**
 * Fetch leaderboard ranking data from the backend
 * @param endpoint - The leaderboard endpoint path (e.g. "today", "weekly?floating=true", "season")
 * @param revalidate - Next.js ISR revalidation interval in seconds
 * @returns The parsed JSON data, or null on failure
 */
export async function fetchLeaderboardData(
  endpoint: string,
  revalidate: number
): Promise<unknown | null> {
  try {
    const res = await fetch(`${BACKEND_ORIGIN}/api/leaderboard/${endpoint}`, {
      headers: {"accept": "application/json"},
      next: { revalidate },
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    // Silently return null during build when backend is not available
    return null;
  }
}

/** Shape returned by fetchAchievements */
export type AchievementsResult = {
  data: unknown[];
  serverOffsetMinutes: number;
};

/**
 * Fetch user achievements for a given timeframe
 * @param timeframe - The achievement timeframe query parameter
 * @param revalidate - Next.js ISR revalidation interval in seconds
 * @returns An object with achievements data and server timezone offset
 */
export async function fetchAchievements(
  timeframe: AchievementTimeframe,
  revalidate: number
): Promise<AchievementsResult> {
  try {
    const res = await fetch(
      `${BACKEND_ORIGIN}/api/stats/users/achievements?timeframe=${timeframe}`,
      {
        headers: {"accept": "application/json"},
        next: { revalidate },
      }
    );
    if (!res.ok) return { data: [], serverOffsetMinutes: 0 };
    const data = await res.json();
    const serverOffsetMinutes = parseInt(
      res.headers.get('X-Server-Timezone-Offset') || '0',
      10
    );
    return { data, serverOffsetMinutes };
  } catch {
    return { data: [], serverOffsetMinutes: 0 };
  }
}

/**
 * Convenience function to fetch both leaderboard and achievements data in parallel
 * This matches the pattern used by all leaderboard pages
 */
export async function fetchLeaderboardPageData(
  endpoint: string,
  timeframe: AchievementTimeframe,
  revalidate: number
): Promise<[unknown | null, AchievementsResult]> {
  return Promise.all([
    fetchLeaderboardData(endpoint, revalidate),
    fetchAchievements(timeframe, revalidate),
  ]);
}