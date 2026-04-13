"use client";

import React, {useMemo} from "react";

type Round = { roundIndex: number; points: number };

const WIDTH = 300;
const HEIGHT = 160;
const PAD = {top: 12, right: 16, bottom: 28, left: 32};

export default function RoundPositionChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const bars = useMemo(() => {
        const groups = new Map<number, { total: number; count: number }>();
        for (const r of rounds) {
            const pos = r.roundIndex ?? 1;
            if (!groups.has(pos)) groups.set(pos, {total: 0, count: 0});
            const g = groups.get(pos)!;
            g.total += r.points;
            g.count++;
        }
        return Array.from(groups.entries())
            .sort(([a], [b]) => a - b)
            .map(([pos, {total, count}]) => ({pos, avg: total / count, count}));
    }, [rounds]);

    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const n = Math.max(bars.length, 1);
    const step = plotW / n;
    const barW = Math.min(40, step * 0.55);

    const yScale = (v: number) => PAD.top + plotH - (v / 5) * plotH;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Avg points by round position</div>
            <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img" aria-label="Average points by round position">
                {/* Y grid & labels */}
                {[0, 1, 2, 3, 4, 5].map(v => (
                    <g key={v}>
                        <line x1={PAD.left} x2={WIDTH - PAD.right} y1={yScale(v)} y2={yScale(v)}
                              stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                        {v % 5 === 0 && (
                            <text x={PAD.left - 4} y={yScale(v) + 4} fontSize="10" fill="var(--color-muted)" textAnchor="end">{v}</text>
                        )}
                    </g>
                ))}
                {/* Bars */}
                {bars.map(({pos, avg}, i) => {
                    const cx = PAD.left + i * step + step / 2;
                    const barH = (avg / 5) * plotH;
                    const x = cx - barW / 2;
                    const y = yScale(avg);
                    return (
                        <g key={pos} aria-label={`Round ${pos}: avg ${avg.toFixed(2)} pts`}>
                            <rect x={x} y={y} width={barW} height={barH} rx={3}
                                  fill="var(--color-primary, #6366f1)" fillOpacity="0.85"/>
                            <text x={cx} y={y - 4} fontSize="10" fill="var(--color-primary, #6366f1)" textAnchor="middle"
                                  fontWeight="600">{avg.toFixed(1)}</text>
                            <text x={cx} y={HEIGHT - PAD.bottom + 14} fontSize="11" fill="var(--color-muted)"
                                  textAnchor="middle">R{pos}</text>
                        </g>
                    );
                })}
                {bars.length === 0 && (
                    <text x={WIDTH / 2} y={HEIGHT / 2} fontSize="12" fill="var(--color-muted)" textAnchor="middle">No data</text>
                )}
            </svg>
        </div>
    );
}
