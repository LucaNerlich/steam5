"use client";

import React, {useCallback, useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

export default function RollingHitRateSpark({rounds}: { rounds: Round[] }): React.ReactElement {
    const DAYS_WINDOW = 30;
    const days = useMemo(() => {
        const now = new Date();
        const dates: string[] = [];
        for (let i = DAYS_WINDOW - 1; i >= 0; i--) {
            const day = new Date(now);
            day.setDate(now.getDate() - i);
            dates.push(day.toISOString().slice(0, 10));
        }
        return dates;
    }, []);
    const width = 600;
    const height = 140;
    const padding = 24;

    const daily = useMemo(() => {
        if (rounds.length === 0) {
            return days.map(date => ({date, hits: 0, total: 0}));
        }
        const firstDay = days[0];
        const lastDay = days[days.length - 1];
        const grouped = new Map<string, {date: string; hits: number; total: number}>();
        for (const round of rounds) {
            if (!round.date || round.date < firstDay || round.date > lastDay) continue;
            const entry = grouped.get(round.date) ?? {date: round.date, hits: 0, total: 0};
            entry.total += 1;
            if (round.selectedBucket === round.actualBucket) entry.hits += 1;
            grouped.set(round.date, entry);
        }
        return days.map(date => grouped.get(date) ?? {date, hits: 0, total: 0});
    }, [rounds, days]);

    const series = useMemo(() => {
        const values: number[] = [];
        const ROLLING_WINDOW_DAYS = 7;
        // Rolling window: for each day, calculate hit rate over trailing 7 days
        for (let i = 0; i < daily.length; i++) {
            const s = Math.max(0, i - ROLLING_WINDOW_DAYS + 1);
            const slice = daily.slice(s, i + 1);
            const total = slice.reduce((sum, day) => sum + day.total, 0);
            const hits = slice.reduce((sum, day) => sum + day.hits, 0);
            values.push(total === 0 ? 0 : Math.round((hits / total) * 100));
        }
        return values;
    }, [daily]);

    const y = useCallback((v: number) => {
        const min = 0, max = 100;
        const t = (v - min) / (max - min);
        return height - 6 - t * (height - 12);
    }, [height]);

    const points = useMemo(() => series.map((v, i) => `${padding + (i / Math.max(1, series.length - 1)) * (width - padding * 2)},${y(v)}`).join(' '), [series, width, y, padding]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Hit rate (rolling last {DAYS_WINDOW} days)</div>
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
                    <polyline fill="none" stroke="var(--color-primary, #6366f1)" strokeWidth="2" points={points} />
                </g>
                {(() => {
                    const len = Math.max(2, series.length);
                    const idxs = [0, Math.floor((len - 1) / 2), len - 1];
                    const labels = ['older', 'mid', 'newer'] as const;
                    return idxs.map((idx, pos) => (
                        <text key={labels[pos]} x={padding + (idx / Math.max(1, len - 1)) * (width - padding * 2)} y={height - 2} fontSize="16" fill="var(--color-muted)" textAnchor={pos === 0 ? 'start' : pos === 2 ? 'end' : 'middle'}>
                            {labels[pos]}
                        </text>
                    ));
                })()}
            </svg>
        </div>
    );
}


