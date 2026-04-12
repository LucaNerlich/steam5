"use client";

import React, {useMemo} from "react";

type Round = { date?: string; points: number };

export default function PerfectDaysCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const {perfectDays, totalDays, pct} = useMemo(() => {
        const byDate = new Map<string, number[]>();
        for (const r of rounds) {
            if (!r.date) continue;
            if (!byDate.has(r.date)) byDate.set(r.date, []);
            byDate.get(r.date)!.push(r.points);
        }
        let perfect = 0;
        for (const pts of byDate.values()) {
            if (pts.length > 0 && pts.every(p => p === 5)) perfect++;
        }
        const total = byDate.size;
        return {perfectDays: perfect, totalDays: total, pct: total > 0 ? Math.round((perfect / total) * 100) : 0};
    }, [rounds]);

    const WIDTH = 220;
    const HEIGHT = 80;
    const R = 28;
    const cx = WIDTH / 2;
    const cy = HEIGHT / 2 + 4;
    const strokeW = 7;
    const circumference = 2 * Math.PI * R;
    const arc = (pct / 100) * circumference;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Perfect days</div>
            <div className="perf-perfect">
                <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-perfect__ring" role="img"
                     aria-label={`${perfectDays} perfect days out of ${totalDays} total`}>
                    {/* Background ring */}
                    <circle cx={cx} cy={cy} r={R} fill="none" stroke="var(--color-border)"
                            strokeWidth={strokeW} strokeOpacity="0.5"/>
                    {/* Progress ring */}
                    {totalDays > 0 && (
                        <circle
                            cx={cx} cy={cy} r={R}
                            fill="none"
                            stroke="var(--color-success, #16a34a)"
                            strokeWidth={strokeW}
                            strokeDasharray={`${arc} ${circumference - arc}`}
                            strokeLinecap="round"
                            transform={`rotate(-90 ${cx} ${cy})`}
                        />
                    )}
                    {/* Center text */}
                    <text x={cx} y={cy - 4} textAnchor="middle" fontSize="16" fontWeight="700"
                          fill="var(--color-success, #16a34a)">{perfectDays}</text>
                    <text x={cx} y={cy + 10} textAnchor="middle" fontSize="9"
                          fill="var(--color-muted)">/ {totalDays} days</text>
                </svg>
                <div className="perf-perfect__stats">
                    <div className="perf-perfect__pct" style={{color: "var(--color-success, #16a34a)"}}>{pct}%</div>
                    <div className="perf-perfect__label">perfect rate</div>
                    <div className="perf-perfect__sub">All rounds scored 5 pts</div>
                </div>
            </div>
        </div>
    );
}
