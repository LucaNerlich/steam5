"use client";

import React, {useMemo} from "react";

type Round = { selectedBucket: string; actualBucket: string };

function bucketOrder(label: string): number {
    const m = label?.match(/^(\d+)/);
    return m ? parseInt(m[1], 10) : -1;
}

export default function BiasBadge({rounds}: { rounds: Round[] }): React.ReactElement {
    const {message, detail, color, kind, highPct, lowPct, hitPct} = useMemo(() => {
        let high = 0, low = 0, hit = 0;
        for (const r of rounds) {
            if (!r.selectedBucket || !r.actualBucket) continue;
            if (r.selectedBucket === r.actualBucket) hit++;
            else if (bucketOrder(r.selectedBucket) > bucketOrder(r.actualBucket)) high++;
            else low++;
        }
        const total = high + low + hit;
        if (total === 0) return {message: "No data yet", detail: "", color: "var(--color-muted)", kind: "neutral", highPct: 0, lowPct: 0, hitPct: 0};
        const hp = Math.round((high / total) * 100);
        const lp = Math.round((low / total) * 100);
        const htp = Math.round((hit / total) * 100);
        if (hp > 45) return {
            message: "You tend to guess too high",
            detail: `${hp}% of guesses overshoot the actual price range`,
            color: "var(--color-warning, #f59e0b)",
            kind: "high", highPct: hp, lowPct: lp, hitPct: htp,
        };
        if (lp > 45) return {
            message: "You tend to guess too low",
            detail: `${lp}% of guesses undershoot the actual price range`,
            color: "var(--color-danger, #ef4444)",
            kind: "low", highPct: hp, lowPct: lp, hitPct: htp,
        };
        return {
            message: "Your guesses are well balanced",
            detail: `${htp}% hit rate across ${total} rounds`,
            color: "var(--color-success, #16a34a)",
            kind: "balanced", highPct: hp, lowPct: lp, hitPct: htp,
        };
    }, [rounds]);

    const WIDTH = 300;
    const HEIGHT = 56;
    const barY = 30;
    const barH = 14;
    const barX = 0;
    const barW = WIDTH;
    const hW = (highPct / 100) * barW;
    const htW = (hitPct / 100) * barW;
    const lW = (lowPct / 100) * barW;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Guess bias</div>
            <div className="perf-badge" style={{"--badge-color": color} as React.CSSProperties}>
                <div className="perf-badge__text">
                    <div className="perf-badge__main" style={{color}}>{message}</div>
                    {detail && <div className="perf-badge__sub">{detail}</div>}
                </div>
            </div>
            {rounds.length > 0 && (
                <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img"
                     aria-label="Breakdown of too high, hit, and too low guesses">
                    {/* Background */}
                    <rect x={barX} y={barY} width={barW} height={barH} rx={3}
                          fill="var(--color-border)" fillOpacity="0.3"/>
                    {/* Stacked segments */}
                    <rect x={barX} y={barY} width={hW} height={barH} rx={3}
                          fill="var(--color-warning, #f59e0b)" fillOpacity="0.85"/>
                    <rect x={barX + hW} y={barY} width={htW} height={barH}
                          fill="var(--color-success, #16a34a)" fillOpacity="0.85"/>
                    <rect x={barX + hW + htW} y={barY} width={lW} height={barH}
                          fill="var(--color-danger, #ef4444)" fillOpacity="0.85"
                          style={{borderRadius: "0 3px 3px 0"}}/>
                    {/* Labels */}
                    <text x={2} y={barY - 4} fontSize="9" fill="var(--color-warning, #f59e0b)">
                        Too high {highPct}%
                    </text>
                    <text x={WIDTH / 2} y={barY - 4} fontSize="9" fill="var(--color-success, #16a34a)" textAnchor="middle">
                        Hit {hitPct}%
                    </text>
                    <text x={WIDTH - 2} y={barY - 4} fontSize="9" fill="var(--color-danger, #ef4444)" textAnchor="end">
                        Too low {lowPct}%
                    </text>
                </svg>
            )}
        </div>
    );
}
