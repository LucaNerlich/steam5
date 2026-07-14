/**
 * Types and utilities for the "hardest games" leaderboard.
 * Server-side prefetch helper mirrors the shared leaderboard fetch pattern.
 */

import {BACKEND_ORIGIN} from "@/lib/backend";

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
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/hardest`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 3600, tags: ["stats-hardest-games"]},
        });
        if (!res.ok) return null;
        return await res.json() as HardestGame[];
    } catch {
        return null;
    }
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
