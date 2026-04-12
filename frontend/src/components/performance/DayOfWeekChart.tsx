"use client";

import React, {useMemo} from "react";

type Round = { date?: string; points: number };

const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const WIDTH = 300;
const HEIGHT = 140;
const PAD = {top: 12, right: 12, bottom: 22, left: 28};

export default function DayOfWeekChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const bars = useMemo(() => {
        const groups: { total: number; count: number }[] = Array.from({length: 7}, () => ({total: 0, count: 0}));
        for (const r of rounds) {
            if (!r.date) continue;
            const dow = (new Date(r.date + "T00:00:00Z").getUTCDay() + 6) % 7; // 0=Mon
            groups[dow].total += r.points;
            groups[dow].count++;
        }
        return groups.map((g, i) => ({
            label: DAY_LABELS[i],
            avg: g.count > 0 ? g.total / g.count : null,
            count: g.count,
        }));
    }, [rounds]);

    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const step = plotW / 7;
    const barW = step * 0.55;

    const yScale = (v: number) => PAD.top + plotH - (v / 5) * plotH;
    const maxAvg = Math.max(...bars.map(b => b.avg ?? 0));

    return (
        <div className="perf-card">
            <div className="perf-card__title">Avg points by day of week</div>
            <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img" aria-label="Average points by day of week">
                {/* Y grid */}
                {[0, 5].map(v => (
                    <g key={v}>
                        <line x1={PAD.left} x2={WIDTH - PAD.right} y1={yScale(v)} y2={yScale(v)}
                              stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                        <text x={PAD.left - 3} y={yScale(v) + 3} fontSize="9" fill="var(--color-muted)" textAnchor="end">{v}</text>
                    </g>
                ))}
                {/* Bars */}
                {bars.map(({label, avg, count}, i) => {
                    const cx = PAD.left + i * step + step / 2;
                    const x = cx - barW / 2;
                    const isMax = avg !== null && avg === maxAvg && count > 0;
                    return (
                        <g key={label} aria-label={avg !== null ? `${label}: avg ${avg.toFixed(2)} pts (${count} rounds)` : `${label}: no data`}>
                            {avg !== null && (
                                <rect x={x} y={yScale(avg)} width={barW} height={(avg / 5) * plotH} rx={2}
                                      fill="var(--color-primary, #6366f1)"
                                      fillOpacity={isMax ? 0.95 : 0.6}/>
                            )}
                            {avg === null && (
                                <rect x={x} y={PAD.top + plotH - 2} width={barW} height={2} rx={1}
                                      fill="var(--color-border)" fillOpacity="0.5"/>
                            )}
                            <text x={cx} y={HEIGHT - PAD.bottom + 13} fontSize="9" fill="var(--color-muted)"
                                  textAnchor="middle">{label}</text>
                        </g>
                    );
                })}
            </svg>
        </div>
    );
}
