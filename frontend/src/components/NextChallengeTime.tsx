"use client";

import useSWR from "swr";

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function NextChallengeTime() {
    const { data: nextChallengeData } = useSWR<{nextChallengeTime: string, serverTimezoneOffset: number}>(
        '/api/review-game/next-challenge-time',
        fetcher,
        {
            refreshInterval: 60000, // Refresh every minute
            revalidateOnFocus: true,
        }
    );

    const nextChallengeTimeLocal = formatChallengeTime(nextChallengeData?.nextChallengeTime);

    if (!nextChallengeTimeLocal) return null;

    return (
        <>Next daily challenge at: <span style={{opacity: 0.7}}>{nextChallengeTimeLocal}</span></>
    );
}

/**
 * Formats the backend's next-challenge timestamp for display, or null when it cannot be parsed.
 *
 * <p>`new Date(value)` never throws — it yields an Invalid Date — so a try/catch could not catch
 * anything, while the real failure mode went unguarded: a malformed timestamp rendered as
 * "NaN:NaN AM" (truthy, so the caller's null check passed it through) instead of hiding the line.</p>
 *
 * @param raw ISO-8601 UTC timestamp from the backend, e.g. "2025-11-12T00:01Z"
 * @returns the local 12-hour time, or null when there is nothing usable to show
 */
export function formatChallengeTime(raw: string | null | undefined): string | null {
    if (!raw) return null;
    const utcDate = new Date(raw);
    if (Number.isNaN(utcDate.getTime())) return null;
    const hours = utcDate.getHours();
    const mins = utcDate.getMinutes();
    const period = hours >= 12 ? 'PM' : 'AM';
    const displayHours = hours === 0 ? 12 : hours > 12 ? hours - 12 : hours;
    return `${displayHours}:${mins.toString().padStart(2, '0')} ${period}`;
}
