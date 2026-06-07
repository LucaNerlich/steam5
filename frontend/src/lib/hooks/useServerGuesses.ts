"use client";

import {useEffect, useState} from "react";
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
function toRoundResult(g: ServerGuess): RoundResult {
    return {
        appId: g.appId,
        pickName: undefined,
        selectedLabel: g.selectedBucket,
        actualBucket: g.actualBucket ?? '',
        totalReviews: g.totalReviews ?? 0,
        correct: g.actualBucket ? g.actualBucket === g.selectedBucket : false,
    };
}

export default function useServerGuesses(disabled: boolean = false): {
    guesses: Record<number, RoundResult>;
    loading: boolean;
} {
    const [serverGuesses, setServerGuesses] = useState<Record<number, RoundResult>>({});
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (disabled) {
            setLoading(false);
            return;
        }
        let cancelled = false;

        async function load() {
            try {
                setLoading(true);
                const res = await fetch('/api/review-game/my/today', {credentials: 'include', cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json() as ServerGuess[];
                if (cancelled) return;
                const map: Record<number, RoundResult> = {};
                for (const g of data) map[g.roundIndex] = toRoundResult(g);
                setServerGuesses(map);
            } catch {
                // ignore
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, [disabled]);

    return {guesses: serverGuesses, loading};
}
