"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

const BUCKET_ORDER = [
    "0-500", "501-2000", "2001-10000", "10001-50000", "50001-200000", "200001+"
];

function bucketIndex(label: string): number {
    const idx = BUCKET_ORDER.indexOf(label);
    return idx >= 0 ? idx : -1;
}

type DistEntry = { distance: number; label: string; count: number; pct: number };

const WIDTH = 300;
const HEIGHT = 160;
const PAD = {top: 12, right: 16, bottom: 28, left: 32};

export default function GuessDistanceChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const bars: DistEntry[] = useMemo(() => {
        const counts = new Map<number, number>();
        let valid = 0;
        for (const r of rounds) {
            const si = bucketIndex(r.selectedBucket);
            const ai = bucketIndex(r.actualBucket);
            if (si < 0 || ai < 0) continue;
            const dist = si - ai;
            counts.set(dist, (counts.get(dist) ?? 0) + 1);
            valid++;
        }
        if (valid === 0) return [];

        const keys = Array.from(counts.keys()).sort((a, b) => a - b);
        return keys.map(d => ({
            distance: d,
            label: d === 0 ? "Exact" : d > 0 ? `+${d}` : `${d}`,
            count: counts.get(d) ?? 0,
            pct: (counts.get(d) ?? 0) / valid,
        }));
    }, [rounds]);

    if (bars.length === 0) {
        return (
            <div className="perf-card">
                <div className="perf-card__title">Guess distance</div>
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>No data</p>
            </div>
        );
    }

    const maxPct = Math.max(...bars.map(b => b.pct), 0.01);
    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const n = bars.length;
    const step = plotW / n;
    const barW = Math.min(36, step * 0.6);

    const yScale = (pct: number) => PAD.top + plotH - (pct / maxPct) * plotH;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Guess distance</div>
            <p style={{color: "var(--color-muted)", fontSize: "0.75rem", margin: "0 0 0.15rem"}}>
                Buckets off from the correct answer
            </p>
            <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img" aria-label="Distribution of guess distance in buckets">
                <line x1={PAD.left} x2={WIDTH - PAD.right}
                      y1={PAD.top + plotH} y2={PAD.top + plotH}
                      stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                {bars.map(({distance, label, count, pct}, i) => {
                    const cx = PAD.left + i * step + step / 2;
                    const x = cx - barW / 2;
                    const barH = (pct / maxPct) * plotH;
                    const y = PAD.top + plotH - barH;
                    const isExact = distance === 0;
                    return (
                        <g key={distance} aria-label={`${label}: ${count} (${(pct * 100).toFixed(0)}%)`}>
                            <rect x={x} y={y} width={barW} height={barH} rx={3}
                                  fill={isExact ? "var(--color-success, #16a34a)" : distance > 0 ? "var(--color-warning, #f59e0b)" : "var(--color-danger, #ef4444)"}
                                  fillOpacity={isExact ? 0.9 : 0.65}/>
                            {count > 0 && (
                                <text x={cx} y={y - 4} fontSize="9"
                                      fill="var(--color-muted)"
                                      textAnchor="middle" fontWeight="600">
                                    {(pct * 100).toFixed(0)}%
                                </text>
                            )}
                            <text x={cx} y={HEIGHT - PAD.bottom + 14} fontSize="10" fill="var(--color-muted)"
                                  textAnchor="middle">{label}</text>
                        </g>
                    );
                })}
            </svg>
        </div>
    );
}
