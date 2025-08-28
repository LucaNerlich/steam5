"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

export default function OutcomeMixBar({rounds}: { rounds: Round[] }): React.ReactElement {
    const WINDOW_ROUNDS = 35;
    const last = useMemo(() => rounds.slice(-WINDOW_ROUNDS), [rounds]);
    const bars = useMemo(() => {
        const counts = {hit: 0, high: 0, low: 0};
        for (const r of last) {
            if (r.selectedBucket === r.actualBucket) counts.hit++;
            else if (r.selectedBucket > r.actualBucket) counts.high++;
            else counts.low++;
        }
        const total = Math.max(1, counts.hit + counts.high + counts.low);
        return [
            {key: 'Hit', value: counts.hit, color: 'var(--color-success, #16a34a)', pct: counts.hit / total},
            {key: 'Too High', value: counts.high, color: 'var(--color-warning, #f59e0b)', pct: counts.high / total},
            {key: 'Too Low', value: counts.low, color: 'var(--color-danger, #ef4444)', pct: counts.low / total},
        ];
    }, [last]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Outcome mix (last {WINDOW_ROUNDS})</div>
            <div className="perf-stacked" role="img" aria-label="Distribution of outcomes">
                {bars.map(part => (
                    <div key={part.key} className="perf-stacked__part" style={{width: `${part.pct * 100}%`, background: part.color}}/>
                ))}
            </div>
            <div className="perf-legend">
                {bars.map(p => (
                    <div key={p.key} className="perf-legend__item">
                        <span className="dot" style={{background: p.color}}/>
                        <span>{p.key}: {p.value}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}


