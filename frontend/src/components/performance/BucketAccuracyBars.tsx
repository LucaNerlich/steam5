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

    return (
        <div className="perf-card">
            <div className="perf-card__title">Accuracy by bucket (last {DAYS_WINDOW} days)</div>
            {stats.length === 0 ? (
                <p className="text-muted">No data</p>
            ) : (
                <div className="bucket-bars" role="img" aria-label="Accuracy by bucket">
                    {stats.map((row) => (
                        <div key={row.label} className="bucket-bars__row">
                            <span className="bucket-bars__label">{row.label}</span>
                            <div className="bucket-bars__track">
                                <div
                                    className="bucket-bars__fill"
                                    style={{width: `${Math.max(0, Math.min(100, row.pct))}%`}}
                                />
                            </div>
                            <span className="bucket-bars__pct">{Math.round(row.pct)}%</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
