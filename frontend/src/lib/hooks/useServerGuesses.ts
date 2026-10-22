"use client";

import useSWR from "swr";
import {useAuth} from "@/contexts/AuthContext";
import type {RoundResult} from "@/lib/storage";

/** Raw per-round guess shape returned by /api/review-game/my/today. */
type ServerGuess = {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket?: string;
    totalReviews?: number;
};

/**
 * Map the backend guess shape onto the {@link RoundResult} shape the rest of the app
 * renders with. Keeping this here means consumers never see backend field names
 * (selectedBucket/actualBucket) — a rename on the backend is absorbed in one place.
 */
export function toRoundResult(g: ServerGuess): RoundResult {
    return {
        appId: g.appId,
        pickName: undefined,
        selectedLabel: g.selectedBucket,
        actualBucket: g.actualBucket ?? '',
        totalReviews: g.totalReviews ?? 0,
        correct: g.actualBucket ? g.actualBucket === g.selectedBucket : false,
    };
}

function isValidServerGuess(g: unknown): g is ServerGuess {
    return typeof g === 'object' && g !== null
        && typeof (g as any).roundIndex === 'number'
        && typeof (g as any).appId === 'number'
        && typeof (g as any).selectedBucket === 'string';
}

/**
 * Fetch today's server guesses. Never rejects — any failure (network, non-OK
 * status, bad JSON, invalid shape) resolves to null so SWR treats it as "no data"
 * without entering its error/retry path.
 */
const myTodayFetcher = async (url: string): Promise<ServerGuess[] | null> => {
    try {
        const res = await fetch(url, {credentials: 'include', cache: 'no-store'});
        if (!res.ok) return null;
        const body: unknown = await res.json();
        if (!Array.isArray(body)) return null;
        // Validate all entries are valid ServerGuess objects
        for (const item of body) {
            if (!isValidServerGuess(item)) return null;
        }
        return body as ServerGuess[];
    } catch {
        return null;
    }
};

export default function useServerGuesses(): {
    guesses: Record<number, RoundResult>;
    loading: boolean;
} {
    const {steamId} = useAuth();

    // Include steamId in the SWR key so guesses are not shared across accounts
    const swrKey = `/api/review-game/my/today?steamId=${steamId ?? 'anon'}`;

    // No cache surgery here: steamId is already part of the key, so entries never collide.
    // Deleting the current key's entry on mount also drops its cache for every other consumer,
    // and a raw Map.delete never emits UNLOAD, so keepPreviousData still renders the old value.

    // SWR owns the fetch lifecycle (dedup, races, cleanup) instead of a manual effect.
    const {data, isLoading} = useSWR<ServerGuess[] | null>(
        swrKey,
        myTodayFetcher,
        {revalidateOnFocus: false, dedupingInterval: 0, keepPreviousData: true},
    );

    const guesses: Record<number, RoundResult> = {};
    if (data) {
        for (const g of data) guesses[g.roundIndex] = toRoundResult(g);
    }

    return {guesses, loading: isLoading};
}
