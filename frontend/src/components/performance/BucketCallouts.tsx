"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

const MIN_ROUNDS = 3;

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

    const WIDTH = 300;
    const HEIGHT = hasData ? 110 : 40;
    const PAD = {top: 8, right: 12, bottom: 8, left: 12};
    const barW = WIDTH - PAD.left - PAD.right;
    const barH = 12;

    function pctBar(pct: number, color: string, y: number) {
        return (
            <g>
                <rect x={PAD.left} y={y} width={barW} height={barH} rx={2}
                      fill="var(--color-border)" fillOpacity="0.3"/>
                <rect x={PAD.left} y={y} width={Math.max(0, pct) * barW} height={barH} rx={2}
                      fill={color} fillOpacity="0.85"/>
                <text x={PAD.left + barW + 4} y={y + barH * 0.85} fontSize="10"
                      fill="var(--color-muted)">{Math.round(pct * 100)}%</text>
            </g>
        );
    }

    return (
        <div className="perf-card">
            <div className="perf-card__title">Best &amp; worst bucket</div>
            {!hasData ? (
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>
                    Need at least {MIN_ROUNDS} rounds per bucket
                </p>
            ) : (
                <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img"
                     aria-label="Best and worst price bucket by hit rate">
                    {/* Best */}
                    <text x={PAD.left} y={PAD.top + 8} fontSize="10" fill="var(--color-muted)">
                        Best: <tspan fontWeight="600" fill="var(--color-success, #16a34a)">{best!.label}</tspan>
                        <tspan fill="var(--color-muted)"> ({best!.total} rounds)</tspan>
                    </text>
                    {pctBar(best!.pct, "var(--color-success, #16a34a)", PAD.top + 14)}

                    {/* Worst */}
                    {worst && worst.label !== best!.label && (
                        <>
                            <text x={PAD.left} y={PAD.top + 50} fontSize="10" fill="var(--color-muted)">
                                Worst: <tspan fontWeight="600" fill="var(--color-danger, #ef4444)">{worst.label}</tspan>
                                <tspan fill="var(--color-muted)"> ({worst.total} rounds)</tspan>
                            </text>
                            {pctBar(worst.pct, "var(--color-danger, #ef4444)", PAD.top + 56)}
                        </>
                    )}
                </svg>
            )}
        </div>
    );
}
