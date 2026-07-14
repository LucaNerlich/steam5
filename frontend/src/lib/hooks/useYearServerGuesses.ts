"use client";

import {useEffect, useState} from "react";
import type {MyYearGuess} from "@/types/year-game";

export default function useYearServerGuesses(disabled: boolean = false): {
    guesses: Record<number, MyYearGuess>;
    loading: boolean;
} {
    const [guesses, setGuesses] = useState<Record<number, MyYearGuess>>({});
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
                const res = await fetch('/api/year-game/my/today', {credentials: 'include', cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json() as MyYearGuess[];
                if (cancelled) return;
                const map: Record<number, MyYearGuess> = {};
                for (const guess of data) map[guess.roundIndex] = guess;
                setGuesses(map);
            } catch {
                // ignore
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        void load();
        return () => {
            cancelled = true;
        };
    }, [disabled]);

    return {guesses, loading};
}
