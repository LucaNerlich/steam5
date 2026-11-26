"use client";

import React from "react";
import {cutoffDateString, leadingBucketOrder} from "@/lib/rounds";

type Round = { selectedBucket: string; actualBucket: string; date?: string };

export default function BucketAccuracyBars({rounds, todayUtc}: { rounds: Round[]; todayUtc: string }): React.ReactElement {
    const DAYS_WINDOW = 30;
    const cutoff = cutoffDateString(DAYS_WINDOW, todayUtc);
    const last = rounds.filter(r => r.date && r.date >= cutoff);

    const stats = (() => {
        const map = new Map<string, { hits: number; total: number }>();
        for (const r of last) {
            if (!r.actualBucket || !r.selectedBucket) continue;
            const key = r.actualBucket;
            const entry = map.get(key) || {hits: 0, total: 0};
            entry.total += 1;
            if (r.selectedBucket === r.actualBucket) entry.hits += 1;
            map.set(key, entry);
        }
        return Array.from(map.entries())
            .sort((a, b) => leadingBucketOrder(a[0]) - leadingBucketOrder(b[0]))
            .map(([label, v]) => ({label, pct: v.total > 0 ? (v.hits / v.total) * 100 : 0}));
    })();

    return (
        <div className="perf-card">
            <div className="perf-card__title">Accuracy by bucket (last {DAYS_WINDOW} days)</div>
            {stats.length === 0 ? (
                <p className="text-muted">No data</p>
            ) : (
                <div className="bucket-bars" role="img" aria-label={`Accuracy by bucket: ${stats.map(s => `${s.label} ${Math.round(s.pct)}%`).join(", ")}`}>
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
