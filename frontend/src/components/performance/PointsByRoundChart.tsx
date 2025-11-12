"use client";

import React, {useMemo} from "react";

type Round = {
    selectedBucket: string;
    actualBucket: string;
    points: number;
    date?: string;
};

export default function PointsByRoundChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const DAYS_WINDOW = 30;
    const last = useMemo(() => {
        if (rounds.length === 0) return [];
        const now = new Date();
        const cutoffDate = new Date(now);
        cutoffDate.setDate(cutoffDate.getDate() - DAYS_WINDOW);
        const cutoffStr = cutoffDate.toISOString().slice(0, 10);
        return rounds.filter(r => r.date && r.date >= cutoffStr);
    }, [rounds]);
    const width = 600;
    const height = 200;
    const padding = 24;
    const xAxisSpace = 20;

    const xScale = (i: number) => {
        if (last.length <= 1) return padding;
        const t = i / (last.length - 1);
        return padding + t * (width - padding * 2);
    };
    const yScale = (p: number) => {
        const min = 0, max = 5;
        const t = (p - min) / (max - min);
        const plotHeight = height - padding * 2 - xAxisSpace;
        return height - padding - xAxisSpace - t * plotHeight;
    };

    return (
        <div className="perf-card">
            <div className="perf-card__title">Points by round (last {DAYS_WINDOW} days)</div>
            <svg viewBox={`0 0 ${width} ${height}`} className="perf-line" role="img" aria-label="Points by round with axes">
                <defs>
                    <linearGradient id="perfLineGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.4"/>
                        <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0"/>
                    </linearGradient>
                </defs>
                <rect x="0" y="0" width={width} height={height} fill="transparent"/>
                <g>
                    <polyline
                        fill="none"
                        stroke="var(--color-primary, #6366f1)"
                        strokeWidth="2"
                        points={last.map((r, i) => `${xScale(i)},${yScale(r.points)}`).join(" ")}
                    />
                    {last.map((r, i) => (
                        <circle key={i} cx={xScale(i)} cy={yScale(r.points)} r="2.5" fill="var(--color-primary, #6366f1)"/>
                    ))}
                </g>
                <g>
                    {[0, 1, 2, 3, 4, 5].map(p => (
                        <line key={p} x1={padding} x2={width - padding} y1={yScale(p)} y2={yScale(p)} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                    ))}
                </g>
                <g aria-hidden="true">
                    {[0, 1, 2, 3, 4, 5].map(p => (
                        <text key={p} x={6} y={yScale(p) + 4} fontSize="11" fill="var(--color-muted)" textAnchor="start">{p}</text>
                    ))}
                    <text x={6} y={12} fontSize="11" fill="var(--color-muted)">Points</text>
                </g>
                <g aria-hidden="true">
                    <line x1={padding} x2={width - padding} y1={height - padding} y2={height - padding} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                    {(() => {
                        const len = Math.max(2, last.length);
                        const idxs = [0, Math.floor((len - 1) / 2), len - 1];
                        return idxs.map((idx, i) => (
                            <text key={i} x={xScale(idx)} y={height - padding + 14} fontSize="11" fill="var(--color-muted)" textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}>
                                {i === 0 ? 'older' : i === 1 ? 'mid' : 'newer'}
                            </text>
                        ));
                    })()}
                </g>
            </svg>
        </div>
    );
}


