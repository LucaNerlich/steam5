"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

export default function BucketAccuracyBars({rounds}: { rounds: Round[] }): React.ReactElement {
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
    const padding = 24;

    const stats = useMemo(() => {
        const map = new Map<string, { hits: number; total: number }>();
        for (const r of last) {
            const key = r.actualBucket;
            const entry = map.get(key) || {hits: 0, total: 0};
            entry.total += 1;
            if (r.selectedBucket === r.actualBucket) entry.hits += 1;
            map.set(key, entry);
        }
        const parseStart = (label: string) => {
            const m = label.match(/^(\d+)/);
            return m ? parseInt(m[1], 10) : Number.MAX_SAFE_INTEGER;
        };
        return Array.from(map.entries())
            .sort((a, b) => parseStart(a[0]) - parseStart(b[0]))
            .map(([label, v]) => ({label, pct: v.total > 0 ? (v.hits / v.total) * 100 : 0}));
    }, [last]);

    const rowH = 26;
    const svgH = Math.max(1, stats.length) * rowH + 10;
    const barX = padding + 100;
    const barW = width - padding * 2 - 112;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Accuracy by bucket (last {DAYS_WINDOW} days)</div>
            {stats.length === 0 ? (
                <p className="text-muted">No data</p>
            ) : (
                <svg viewBox={`0 0 ${width} ${svgH}`} className="perf-line" role="img" aria-label="Accuracy by bucket">
                    <rect x="0" y="0" width={width} height={svgH} fill="transparent"/>
                    {stats.map((row, i) => {
                        const y = 8 + i * rowH;
                        const filled = Math.max(0, Math.min(1, row.pct / 100)) * barW;
                        return (
                            <g key={row.label}>
                                <text x={padding} y={y + 14} fontSize="14" fill="var(--color-muted)" textAnchor="start">{row.label}</text>
                                <rect x={barX} y={y} width={barW} height={14} fill="var(--color-border)" />
                                <rect x={barX} y={y} width={filled} height={14} fill="var(--color-primary, #6366f1)" />
                                <text x={barX + barW + 4} y={y + 14} fontSize="14" fill="var(--color-muted)" textAnchor="start">{Math.round(row.pct)}%</text>
                            </g>
                        );
                    })}
                </svg>
            )}
        </div>
    );
}


