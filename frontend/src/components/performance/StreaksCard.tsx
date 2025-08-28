"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

export default function StreaksCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const {current, longest} = useMemo(() => {
        let cur = 0; let best = 0;
        for (let i = 0; i < rounds.length; i++) {
            if (rounds[i].selectedBucket === rounds[i].actualBucket) { cur++; best = Math.max(best, cur); } else { cur = 0; }
        }
        let tail = 0;
        for (let i = rounds.length - 1; i >= 0; i--) { if (rounds[i].selectedBucket === rounds[i].actualBucket) tail++; else break; }
        return {current: tail, longest: best};
    }, [rounds]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Streaks</div>
            <dl className="stats-grid" aria-label="Current and longest hit streaks">
                <dt>Current hit streak</dt>
                <dd>{current}</dd>
                <dt>Longest hit streak</dt>
                <dd>{longest}</dd>
            </dl>
        </div>
    );
}


