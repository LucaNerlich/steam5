"use client";

import React, {useMemo} from "react";

type Round = { points: number; date?: string };

type DayBucket = { label: string; count: number; pct: number; color: string };

export default function PerfectDaysCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const buckets: DayBucket[] = useMemo(() => {
        const dayTotals = new Map<string, number>();
        for (const r of rounds) {
            if (!r.date) continue;
            dayTotals.set(r.date, (dayTotals.get(r.date) ?? 0) + r.points);
        }
        const totals = Array.from(dayTotals.values());
        if (totals.length === 0) return [];

        const n = totals.length;
        const perfect = totals.filter(t => t === 25).length;
        const great = totals.filter(t => t >= 20 && t < 25).length;
        const good = totals.filter(t => t >= 15 && t < 20).length;
        const rest = n - perfect - great - good;

        return [
            {label: "25 pts (perfect)", count: perfect, pct: perfect / n, color: "var(--color-success, #16a34a)"},
            {label: "20–24 pts", count: great, pct: great / n, color: "var(--color-primary, #6366f1)"},
            {label: "15–19 pts", count: good, pct: good / n, color: "var(--color-warning, #f59e0b)"},
            {label: "< 15 pts", count: rest, pct: rest / n, color: "var(--color-danger, #ef4444)"},
        ];
    }, [rounds]);

    if (buckets.length === 0) {
        return (
            <div className="perf-card">
                <div className="perf-card__title">Daily score tiers</div>
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>No data</p>
            </div>
        );
    }

    const total = buckets.reduce((s, b) => s + b.count, 0);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Daily score tiers</div>
            <p style={{color: "var(--color-muted)", fontSize: "0.75rem", margin: "0 0 0.35rem"}}>
                {total} day{total !== 1 ? "s" : ""} played
            </p>
            <div className="perf-stacked" role="img" aria-label="Distribution of daily score tiers">
                {buckets.map(b => (
                    <div key={b.label} className="perf-stacked__part"
                         style={{width: `${b.pct * 100}%`, background: b.color}}/>
                ))}
            </div>
            <div className="perf-legend">
                {buckets.map(b => (
                    <div key={b.label} className="perf-legend__item">
                        <span className="dot" style={{background: b.color}}/>
                        <span>{b.label}: {b.count}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}
