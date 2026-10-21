"use client";

import React from "react";
import {outcomeCounts} from "@/lib/rounds";

type Round = { selectedBucket: string; actualBucket: string };

export default function BiasBadge({rounds}: { rounds: Round[] }): React.ReactElement {
    const {message, detail, color, highPct, lowPct, hitPct} = (() => {
        const {hits: hit, tooHigh: high, tooLow: low} = outcomeCounts(rounds);
        const total = high + low + hit;
        if (total === 0) return {message: "No data yet", detail: "", color: "var(--color-muted)", highPct: 0, lowPct: 0, hitPct: 0};
        const hp = Math.round((high / total) * 100);
        const lp = Math.round((low / total) * 100);
        const htp = Math.round((hit / total) * 100);
        if (hp > 45) return {
            message: "You tend to guess too high",
            detail: `${hp}% of guesses overshoot the actual price range`,
            color: "var(--color-warning)", highPct: hp, lowPct: lp, hitPct: htp,
        };
        if (lp > 45) return {
            message: "You tend to guess too low",
            detail: `${lp}% of guesses undershoot the actual price range`,
            color: "var(--color-danger)", highPct: hp, lowPct: lp, hitPct: htp,
        };
        return {
            message: "Your guesses are well balanced",
            detail: `${htp}% hit rate across ${total} rounds`,
            color: "var(--color-success-text)", highPct: hp, lowPct: lp, hitPct: htp,
        };
    })();

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
            <div className="perf-badge">
                <div>
                    <div className="perf-badge__main" style={{color}}>{message}</div>
                    {detail && <div className="perf-badge__sub">{detail}</div>}
                </div>
            </div>
            {rounds.length > 0 && (
                <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img"
                     aria-label={`Guess bias: ${highPct.toFixed(0)}% too high, ${hitPct.toFixed(0)}% hits, ${lowPct.toFixed(0)}% too low`}>
                    {/* Background */}
                    <rect x={barX} y={barY} width={barW} height={barH} rx={3}
                          fill="var(--color-border)" fillOpacity="0.3"/>
                    {/* Stacked segments */}
                    <rect x={barX} y={barY} width={hW} height={barH} rx={3}
                          fill="var(--color-warning)" fillOpacity="0.85"/>
                    <rect x={barX + hW} y={barY} width={htW} height={barH}
                          fill="var(--color-success)" fillOpacity="0.85"/>
                    <rect x={barX + hW + htW} y={barY} width={lW} height={barH}
                          fill="var(--color-danger)" fillOpacity="0.85"
                          style={{borderRadius: "0 3px 3px 0"}}/>
                    {/* Labels */}
                    <text x={2} y={barY - 4} fontSize="9" fill="var(--color-warning)">
                        Too high {highPct}%
                    </text>
                    <text x={WIDTH / 2} y={barY - 4} fontSize="9" fill="var(--color-success-text)" textAnchor="middle">
                        Hit {hitPct}%
                    </text>
                    <text x={WIDTH - 2} y={barY - 4} fontSize="9" fill="var(--color-danger)" textAnchor="end">
                        Too low {lowPct}%
                    </text>
                </svg>
            )}
        </div>
    );
}
