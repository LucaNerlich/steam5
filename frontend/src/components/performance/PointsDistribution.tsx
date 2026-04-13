"use client";

import React, {useMemo} from "react";

type Round = { points: number };

const WIDTH = 300;
const HEIGHT = 160;
const PAD = {top: 12, right: 16, bottom: 28, left: 32};
const POINT_VALUES = [0, 1, 2, 3, 5];

export default function PointsDistribution({rounds}: { rounds: Round[] }): React.ReactElement {
    const bars = useMemo(() => {
        const counts = new Map<number, number>();
        for (const v of POINT_VALUES) counts.set(v, 0);
        for (const r of rounds) {
            counts.set(r.points, (counts.get(r.points) ?? 0) + 1);
        }
        const total = Math.max(1, rounds.length);
        return POINT_VALUES.map(pts => ({
            pts,
            count: counts.get(pts) ?? 0,
            pct: (counts.get(pts) ?? 0) / total,
        }));
    }, [rounds]);

    const maxPct = Math.max(...bars.map(b => b.pct), 0.01);
    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const n = bars.length;
    const step = plotW / n;
    const barW = Math.min(40, step * 0.55);

    const yScale = (pct: number) => PAD.top + plotH - (pct / maxPct) * plotH;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Points distribution</div>
            <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img" aria-label="Distribution of points scored across all rounds">
                {/* Y grid */}
                {[0, 1].map(frac => (
                    <line key={frac} x1={PAD.left} x2={WIDTH - PAD.right}
                          y1={PAD.top + plotH - frac * plotH} y2={PAD.top + plotH - frac * plotH}
                          stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                ))}
                {bars.map(({pts, count, pct}, i) => {
                    const cx = PAD.left + i * step + step / 2;
                    const x = cx - barW / 2;
                    const barH = maxPct > 0 ? (pct / maxPct) * plotH : 0;
                    const y = PAD.top + plotH - barH;
                    const isHit = pts === 5;
                    return (
                        <g key={pts} aria-label={`${pts} pts: ${count} rounds (${(pct * 100).toFixed(0)}%)`}>
                            <rect x={x} y={y} width={barW} height={barH} rx={3}
                                  fill={isHit ? "var(--color-success, #16a34a)" : "var(--color-primary, #6366f1)"}
                                  fillOpacity={isHit ? 0.9 : 0.7}/>
                            {count > 0 && (
                                <text x={cx} y={y - 4} fontSize="10"
                                      fill={isHit ? "var(--color-success, #16a34a)" : "var(--color-primary, #6366f1)"}
                                      textAnchor="middle" fontWeight="600">
                                    {(pct * 100).toFixed(0)}%
                                </text>
                            )}
                            <text x={cx} y={HEIGHT - PAD.bottom + 14} fontSize="11" fill="var(--color-muted)"
                                  textAnchor="middle">{pts}pt{pts !== 1 ? "s" : ""}</text>
                        </g>
                    );
                })}
                {rounds.length === 0 && (
                    <text x={WIDTH / 2} y={HEIGHT / 2} fontSize="12" fill="var(--color-muted)" textAnchor="middle">No data</text>
                )}
            </svg>
        </div>
    );
}
