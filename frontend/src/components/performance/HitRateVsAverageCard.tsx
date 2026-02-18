"use client";

import React, {useMemo} from "react";
import useSWR from "swr";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

const DAYS_WINDOW = 30;
const WIDTH = 600;
const PADDING = 24;
const BAR_W = WIDTH - PADDING * 2 - 60;

function Row({y, label, pct, color}: { y: number; label: string; pct: number; color: string }) {
    return (
        <g>
            <text x={PADDING} y={y - 2} fontSize="16" fill="var(--color-muted)" textAnchor="start">{label}</text>
            <rect x={PADDING} y={y} width={BAR_W} height={16} fill="var(--color-border)"/>
            <rect x={PADDING} y={y} width={Math.max(0, Math.min(1, pct / 100)) * BAR_W} height={14} fill={color}/>
            <text x={PADDING + BAR_W + 4} y={y + 12} fontSize="14" fill="var(--color-muted)"
                  textAnchor="start">{Math.round(pct)}%
            </text>
        </g>
    );
}

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => r.json());

export default function HitRateVsAverageCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const last = useMemo(() => {
        if (rounds.length === 0) return [];
        const now = new Date();
        const cutoffDate = new Date(now);
        cutoffDate.setDate(cutoffDate.getDate() - DAYS_WINDOW);
        const cutoffStr = cutoffDate.toISOString().slice(0, 10);
        return rounds.filter(r => r.date && r.date >= cutoffStr);
    }, [rounds]);

    const myHitRate = useMemo(() => {
        if (last.length === 0) return 0;
        const hits = last.filter(r => r.selectedBucket === r.actualBucket).length;
        return (hits / last.length) * 100;
    }, [last]);

    const {data: leaders} = useSWR<Array<{
        hits: number;
        rounds: number
    }>>("/api/leaderboard/all", fetcher, {refreshInterval: 300000, revalidateOnFocus: false});
    const globalAvg = useMemo(() => {
        if (!leaders || leaders.length === 0) return null as number | null;
        let h = 0, t = 0;
        for (const l of leaders) {
            h += (l.hits || 0);
            t += (l.rounds || 0);
        }
        if (t <= 0) return null;
        return (h / t) * 100;
    }, [leaders]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Hit rate vs average</div>
            <svg viewBox={`0 0 ${WIDTH} 96`} className="perf-line" role="img"
                 aria-label="Your hit rate vs global average">
                <Row y={22} label={`You (last ${DAYS_WINDOW} days)`} pct={myHitRate}
                     color={'var(--color-primary, #6366f1)'}/>
                <Row y={52} label={'All players'} pct={globalAvg ?? myHitRate} color={'var(--color-success, #16a34a)'}/>
            </svg>
        </div>
    );
}


