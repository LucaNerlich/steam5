"use client";

import React from "react";
import {cutoffDateString, outcomeCounts} from "@/lib/rounds";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

export default function OutcomeMixBar({rounds}: { rounds: Round[] }): React.ReactElement {
    const DAYS_WINDOW = 30;
    const cutoff = cutoffDateString(DAYS_WINDOW);
    const last = rounds.filter(r => r.date && r.date >= cutoff);
    const bars = (() => {
        const {hits, tooHigh, tooLow} = outcomeCounts(last);
        const total = Math.max(1, hits + tooHigh + tooLow);
        return [
            {key: 'Hit', value: hits, color: 'var(--color-success, #16a34a)', pct: hits / total},
            {key: 'Too High', value: tooHigh, color: 'var(--color-warning, #f59e0b)', pct: tooHigh / total},
            {key: 'Too Low', value: tooLow, color: 'var(--color-danger, #ef4444)', pct: tooLow / total},
        ];
    })();

    return (
        <div className="perf-card">
            <div className="perf-card__title">Outcome mix (last {DAYS_WINDOW} days)</div>
            <div className="perf-stacked" role="img" aria-label={`Outcome mix: ${bars.map(b => `${b.key} ${b.value} (${Math.round(b.pct * 100)}%)`).join(", ")}`}>
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


