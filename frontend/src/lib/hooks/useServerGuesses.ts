"use client";

import {useEffect, useState} from "react";

export type ServerGuess = {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket?: string;
    totalReviews?: number;
};

export default function useServerGuesses(): Record<number, ServerGuess> {
    const [serverGuesses, setServerGuesses] = useState<Record<number, ServerGuess>>({});

    useEffect(() => {
        let cancelled = false;

        async function load() {
            try {
                const res = await fetch('/api/review-game/my/today', {credentials: 'include', cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json() as ServerGuess[];
                if (cancelled) return;
                const map: Record<number, ServerGuess> = {};
                for (const g of data) map[g.roundIndex] = g;
                setServerGuesses(map);
            } catch {
                // ignore
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, []);

    return serverGuesses;
}


