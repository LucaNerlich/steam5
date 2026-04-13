"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

const MIN_ROUNDS = 20;

function PctBar({pct, color}: { pct: number; color: string }) {
    const widthPct = Math.max(0, Math.min(1, pct)) * 100;
    return (
        <div className="bucket-callout__bar-track">
            <div className="bucket-callout__bar-bg">
                <div
                    className="bucket-callout__bar-fill"
                    style={{width: `${widthPct}%`, backgroundColor: color}}
                />
            </div>
            <span className="bucket-callout__bar-pct">{Math.round(pct * 100)}%</span>
        </div>
    );
}

export default function BucketCallouts({rounds}: { rounds: Round[] }): React.ReactElement {
    const {best, worst} = useMemo(() => {
        const stats = new Map<string, { hits: number; total: number }>();
        for (const r of rounds) {
            if (!r.actualBucket) continue;
            if (!stats.has(r.actualBucket)) stats.set(r.actualBucket, {hits: 0, total: 0});
            const s = stats.get(r.actualBucket)!;
            s.total++;
            if (r.selectedBucket === r.actualBucket) s.hits++;
        }
        const qualified = Array.from(stats.entries())
            .filter(([, s]) => s.total >= MIN_ROUNDS)
            .map(([label, s]) => ({label, pct: s.hits / s.total, ...s}));
        if (qualified.length === 0) return {best: null, worst: null};
        qualified.sort((a, b) => b.pct - a.pct);
        return {
            best: qualified[0],
            worst: qualified.length > 1 ? qualified[qualified.length - 1] : null,
        };
    }, [rounds]);

    const hasData = best !== null;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Best &amp; worst bucket</div>
            {!hasData ? (
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>
                    Need at least {MIN_ROUNDS} rounds per bucket
                </p>
            ) : (
                <div className="bucket-callout" role="img" aria-label="Best and worst bucket by hit rate">
                    <div className="bucket-callout__row">
                        <span className="bucket-callout__label">
                            Best: <strong className="bucket-callout__label--success">{best!.label}</strong>
                            <span className="bucket-callout__rounds">({best!.total} rounds)</span>
                        </span>
                        <PctBar pct={best!.pct} color="var(--color-success, #16a34a)" />
                    </div>
                    {worst && worst.label !== best!.label && (
                        <div className="bucket-callout__row">
                            <span className="bucket-callout__label">
                                Worst: <strong className="bucket-callout__label--danger">{worst.label}</strong>
                                <span className="bucket-callout__rounds">({worst.total} rounds)</span>
                            </span>
                            <PctBar pct={worst.pct} color="var(--color-danger, #ef4444)" />
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
