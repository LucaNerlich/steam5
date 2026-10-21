/**
 * Types and utilities for the "hardest games" leaderboard.
 * Server-side prefetch helper mirrors the shared leaderboard fetch pattern.
 */

import {fetchBackendJson} from "@/lib/backend";

export type DeceptionDirection = "over" | "under" | "none";

export type HardestGame = {
    appId: number;
    appName: string;
    avgScore: number;
    playerCount: number;
    deceptionRate: number;
    deceptionDirection: DeceptionDirection;
    mostCommonWrongBucket: string | null;
    mostCommonWrongBucketCount: number | null;
    actualBucket: string;
    latestPickDate: string;
};

/**
 * Server-side prefetch for the hardest games list. Returns null on network
 * or backend failure so callers can render a fallback UI.
 */
export async function fetchHardestGames(): Promise<HardestGame[] | null> {
    const {data} = await fetchBackendJson<HardestGame[]>(
        '/api/stats/game/hardest',
        {revalidate: 3600, tags: ["stats-hardest-games"]},
    );
    return data;
}

export function formatDeception(game: HardestGame): string {
    if (game.deceptionDirection === "none") return "—";
    const pct = Math.round(game.deceptionRate * 100);
    const emoji = game.deceptionDirection === "over" ? "🔺" : "🔻";
    const label = game.deceptionDirection === "over" ? "over-guessed" : "under-guessed";
    return `${emoji} ${label} ${pct}%`;
}

export function formatMostMissed(game: HardestGame): string {
    if (!game.mostCommonWrongBucket || game.mostCommonWrongBucketCount === null) {
        return game.actualBucket ? `— → ✓ ${game.actualBucket}` : "—";
    }
    return `${game.mostCommonWrongBucket} (${game.mostCommonWrongBucketCount}×) → ✓ ${game.actualBucket}`;
}
