"use client";

import useSWR from "swr";
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

/**
 * Fetch today's server guesses. Never rejects — any failure (network, non-OK
 * status, bad JSON) resolves to null so SWR treats it as "no data" without
 * entering its error/retry path.
 */
const myTodayFetcher = async (url: string): Promise<ServerGuess[] | null> => {
    try {
        const res = await fetch(url, {credentials: 'include', cache: 'no-store'});
        if (!res.ok) return null;
        return await res.json() as ServerGuess[];
    } catch {
        return null;
    }
};

export default function useServerGuesses(disabled: boolean = false): {
    guesses: Record<number, RoundResult>;
    loading: boolean;
} {
    // SWR owns the fetch lifecycle (dedup, races, cleanup) instead of a manual
    // effect. A null key disables fetching; keepPreviousData keeps the last map
    // while disabled, matching the previous behavior of leaving state untouched.
    const {data, isLoading} = useSWR<ServerGuess[] | null>(
        disabled ? null : '/api/review-game/my/today',
        myTodayFetcher,
        {revalidateOnFocus: false, dedupingInterval: 0, keepPreviousData: true},
    );

    const guesses: Record<number, RoundResult> = {};
    if (data) {
        for (const g of data) guesses[g.roundIndex] = toRoundResult(g);
    }

    return {guesses, loading: !disabled && isLoading};
}
