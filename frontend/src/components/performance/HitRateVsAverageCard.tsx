"use client";

import React from "react";
import useSWR from "swr";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

const DAYS_WINDOW = 30;
const WIDTH = 600;
const PADDING = 24;
const BAR_W = WIDTH - PADDING * 2 - 60;

function Row({y, label, pct, color}: { y: number; label: string; pct: number | null; color: string }) {
    return (
        <g>
            <text x={PADDING} y={y - 2} fontSize="16" fill="var(--color-muted)" textAnchor="start">{label}</text>
            <rect x={PADDING} y={y} width={BAR_W} height={16} fill="var(--color-border)"/>
            {pct === null ? (
                <text x={PADDING} y={y + 12} fontSize="14" fill="var(--color-muted)" textAnchor="start">No data</text>
            ) : (
                <>
                    <rect x={PADDING} y={y} width={Math.max(0, Math.min(1, pct / 100)) * BAR_W} height={14} fill={color}/>
                    <text x={PADDING + BAR_W + 4} y={y + 12} fontSize="14" fill="var(--color-muted)"
                          textAnchor="start">{Math.round(pct)}%
                    </text>
                </>
            )}
        </g>
    );
}

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

function cutoffDateStr(daysWindow: number): string {
    const now = new Date();
    const cutoffDate = new Date(now);
    cutoffDate.setDate(cutoffDate.getDate() - daysWindow);
    return cutoffDate.toISOString().slice(0, 10);
}

export default function HitRateVsAverageCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const cutoff = cutoffDateStr(DAYS_WINDOW);

    const myHitRate = (() => {
        let eligible = 0;
        let hits = 0;
        for (const r of rounds) {
            if (r.date && r.date >= cutoff) {
                eligible++;
                if (r.selectedBucket === r.actualBucket) hits++;
            }
        }
        return eligible === 0 ? 0 : (hits / eligible) * 100;
    })();

    const {data: leaders} = useSWR<Array<{
        hits: number;
        rounds: number
    }>>("/api/leaderboard/all", fetcher, {refreshInterval: 300000, revalidateOnFocus: false});
    const globalAvg = (() => {
        if (!Array.isArray(leaders) || leaders.length === 0) return null as number | null;
        let h = 0, t = 0;
        for (const l of leaders) {
            h += (l.hits || 0);
            t += (l.rounds || 0);
        }
        if (t <= 0) return null;
        return (h / t) * 100;
    })();

    return (
        <div className="perf-card">
            <div className="perf-card__title">Hit rate vs average</div>
            <svg viewBox={`0 0 ${WIDTH} 96`} className="perf-line" role="img"
                 aria-label="Your hit rate vs global average">
                <Row y={22} label={`You (last ${DAYS_WINDOW} days)`} pct={myHitRate}
                     color={'var(--color-primary, #6366f1)'}/>
                <Row y={52} label={'All players (all‑time)'} pct={globalAvg} color={'var(--color-success, #16a34a)'}/>
            </svg>
        </div>
    );
}


