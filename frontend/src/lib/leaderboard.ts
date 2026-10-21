/**
 * Shared server-side fetch utilities for leaderboard pages.
 * Used in Next.js server components to pre-fetch data with ISR caching.
 */

import {BACKEND_ORIGIN, fetchBackendJson} from "@/lib/backend";
import type {UserAchievement} from "@/lib/achievements";
export {BACKEND_ORIGIN};

/** One row of /api/leaderboard/*; the shape LeaderboardTable renders. */
export type LeaderEntry = {
    steamId: string;
    personaName: string;
    totalPoints: number;
    rounds: number;
    hits: number;
    flops: number;
    tooHigh: number;
    tooLow: number;
    avgPoints: number;
    streak: number;
    avatar?: string | null;
    profileUrl?: string | null;
};

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
): Promise<LeaderEntry[] | null> {
  const {data} = await fetchBackendJson<LeaderEntry[]>(`/api/leaderboard/${endpoint}`, {revalidate});
  return data;
}

/** Shape returned by fetchAchievements */
export type AchievementsResult = {
  data: UserAchievement[];
};

/**
 * SWR fetcher for the leaderboard-style endpoints: unwraps `{data, refreshedAt}`, relays the
 * backend's freshness header, and throws on a non-ok response (so SWR keeps the previous data
 * and reports the error instead of rendering an empty table).
 *
 * @param url - the route to fetch
 * @returns the payload and the `X-Leaderboard-Refreshed-At` value when the backend sent one
 */
export async function fetchRefreshedJson<T>(url: string): Promise<{data: T; refreshedAt: string | null}> {
    const response = await fetch(url, {
        headers: {accept: 'application/json'},
        cache: 'no-cache', // revalidate with the server, but let the browser reuse a fresh copy
    });
    if (!response.ok) throw new Error(`Failed to load ${url}: ${response.status}`);
    const data = (await response.json()) as T;
    return {data, refreshedAt: response.headers.get('X-Leaderboard-Refreshed-At')};
}

/**
 * Fetch user achievements for a given timeframe
 * @param timeframe - The achievement timeframe query parameter
 * @param revalidate - Next.js ISR revalidation interval in seconds
 * @returns An object with the achievements data
 */
export async function fetchAchievements(
  timeframe: AchievementTimeframe,
  revalidate: number
): Promise<AchievementsResult> {
  const {data} = await fetchBackendJson<UserAchievement[]>(
    `/api/stats/users/achievements?timeframe=${timeframe}`,
    {revalidate},
  );
  return {data: data ?? []};
}

/**
 * Convenience function to fetch both leaderboard and achievements data in parallel
 * This matches the pattern used by all leaderboard pages
 */
export async function fetchLeaderboardPageData(
  endpoint: string,
  timeframe: AchievementTimeframe,
  revalidate: number
): Promise<[LeaderEntry[] | null, AchievementsResult]> {
  return Promise.all([
    fetchLeaderboardData(endpoint, revalidate),
    fetchAchievements(timeframe, revalidate),
  ]);
}

/** Built once at module scope so formatRefreshedAt doesn't rebuild it per call. */
const REFRESHED_AT_FORMAT = new Intl.DateTimeFormat("en-US", {dateStyle: 'medium', timeStyle: 'short'});

/**
 * Formats an ISO-8601 timestamp (e.g. from the X-Leaderboard-Refreshed-At response
 * header) as a localized date+time string using the browser's own locale/timezone.
 * Returns null for missing or unparseable input, so callers can hide the "last
 * updated" paragraph entirely rather than show a broken timestamp.
 */
export function formatRefreshedAt(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return REFRESHED_AT_FORMAT.format(date);
}