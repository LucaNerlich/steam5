"use client";

import React, {useMemo} from "react";
import useSWR from "swr";

type Round = { selectedBucket: string; actualBucket: string };

export default function HitRateVsAverageCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const WINDOW_ROUNDS = 35;
    const last = useMemo(() => rounds.slice(-WINDOW_ROUNDS), [rounds]);
    const width = 600;
    const padding = 24;

    const myHitRate = useMemo(() => {
        if (last.length === 0) return 0;
        const hits = last.filter(r => r.selectedBucket === r.actualBucket).length;
        return (hits / last.length) * 100;
    }, [last]);

    const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => r.json());
    const {data: leaders} = useSWR<Array<{hits: number; rounds: number}>>("/api/leaderboard/all", fetcher, {refreshInterval: 300000, revalidateOnFocus: false});
    const globalAvg = useMemo(() => {
        if (!leaders || leaders.length === 0) return null as number | null;
        let h = 0, t = 0; for (const l of leaders) { h += (l.hits||0); t += (l.rounds||0); }
        if (t <= 0) return null; return (h / t) * 100;
    }, [leaders]);

    const barX = padding;
    const barW = width - padding * 2 - 60;

    const Row = ({y, label, pct, color}: {y: number; label: string; pct: number; color: string}) => (
        <g>
            <text x={barX} y={y - 2} fontSize="16" fill="var(--color-muted)" textAnchor="start">{label}</text>
            <rect x={barX} y={y} width={barW} height={16} fill="var(--color-border)" />
            <rect x={barX} y={y} width={Math.max(0, Math.min(1, pct / 100)) * barW} height={14} fill={color} />
            <text x={barX + barW + 4} y={y + 12} fontSize="14" fill="var(--color-muted)" textAnchor="start">{Math.round(pct)}%</text>
        </g>
    );

    return (
        <div className="perf-card">
            <div className="perf-card__title">Hit rate vs average</div>
            <svg viewBox={`0 0 ${width} 96`} className="perf-line" role="img" aria-label="Your hit rate vs global average">
                <Row y={22} label={'You (last 35)'} pct={myHitRate} color={'var(--color-primary, #6366f1)'} />
                <Row y={52} label={'All players'} pct={globalAvg ?? myHitRate} color={'var(--color-success, #16a34a)'} />
            </svg>
        </div>
    );
}


