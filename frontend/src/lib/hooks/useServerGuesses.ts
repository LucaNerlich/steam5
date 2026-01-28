"use client";

import {useEffect, useState} from "react";

export type ServerGuess = {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket?: string;
    totalReviews?: number;
};

export default function useServerGuesses(disabled: boolean = false): {
    guesses: Record<number, ServerGuess>;
    loading: boolean;
} {
    const [serverGuesses, setServerGuesses] = useState<Record<number, ServerGuess>>({});
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
                const map: Record<number, ServerGuess> = {};
                for (const g of data) map[g.roundIndex] = g;
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


