"use client";

import React, {useCallback, useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

export default function RollingHitRateSpark({rounds}: { rounds: Round[] }): React.ReactElement {
    const WINDOW_ROUNDS = 35;
    const last = useMemo(() => rounds.slice(-WINDOW_ROUNDS), [rounds]);
    const width = 600;
    const height = 140;
    const padding = 24;

    const series = useMemo(() => {
        const values: number[] = [];
        for (let i = 0; i < last.length; i++) {
            const s = Math.max(0, i - WINDOW_ROUNDS + 1);
            const slice = last.slice(s, i + 1);
            const hits = slice.filter(r => r.selectedBucket === r.actualBucket).length;
            values.push(Math.round((hits / slice.length) * 100));
        }
        return values;
    }, [last]);

    const y = useCallback((v: number) => {
        const min = 0, max = 100;
        const t = (v - min) / (max - min);
        return height - 6 - t * (height - 12);
    }, [height]);

    const path = useMemo(() => series.map((v, i) => `${i === 0 ? 'M' : 'L'}${24 + (i / Math.max(1, series.length - 1)) * (width - 48)},${y(v)}`).join(' '), [series, width, y]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Hit rate (rolling last {WINDOW_ROUNDS})</div>
            <svg viewBox={`0 0 ${width} ${height}`} className="perf-spark" role="img" aria-label="Rolling hit rate with axes">
                {([0,25,50,75,100] as const).map(p => (
                    <g key={p}>
                        <line x1={padding} x2={width - padding} y1={y(p)} y2={y(p)} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1" />
                        <text x={padding - 4} y={y(p) + 3} fontSize="16" fill="var(--color-muted)" textAnchor="end">{p}%</text>
                    </g>
                ))}
                <defs>
                    <clipPath id="sparkClip">
                        <rect x={padding} y={0} width={width - padding * 2} height={height} />
                    </clipPath>
                </defs>
                <g clipPath="url(#sparkClip)">
                    <polyline fill="none" stroke="var(--color-primary, #6366f1)" strokeWidth="2" points={path.replace(/M|L/g, '').trim()} />
                </g>
                {(() => {
                    const len = Math.max(2, series.length);
                    const idxs = [0, Math.floor((len - 1) / 2), len - 1];
                    return idxs.map((idx, i) => (
                        <text key={i} x={padding + (idx / Math.max(1, len - 1)) * (width - padding * 2)} y={height - 2} fontSize="16" fill="var(--color-muted)" textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}>
                            {i === 0 ? 'older' : i === 1 ? 'mid' : 'newer'}
                        </text>
                    ));
                })()}
            </svg>
        </div>
    );
}


