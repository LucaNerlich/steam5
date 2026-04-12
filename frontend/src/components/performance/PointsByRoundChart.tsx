"use client";

import React, {useMemo} from "react";

type Round = {
    roundIndex?: number;
    selectedBucket: string;
    actualBucket: string;
    points: number;
    date?: string;
};

const DAY_MS = 24 * 60 * 60 * 1000;

function fmtDate(dateStr: string): string {
    return new Date(dateStr + "T00:00:00Z").toLocaleDateString("en-US", {
        month: "short", day: "numeric", timeZone: "UTC",
    });
}

export default function PointsByRoundChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const DAYS_WINDOW = 30;

    const {last, windowStartMs, windowEndMs} = useMemo(() => {
        const now = new Date();
        const todayStr = now.toISOString().slice(0, 10);
        const cutoff = new Date(now);
        cutoff.setDate(cutoff.getDate() - DAYS_WINDOW);
        const cutoffStr = cutoff.toISOString().slice(0, 10);
        const startMs = new Date(cutoffStr + "T00:00:00Z").getTime();
        const endMs = new Date(todayStr + "T00:00:00Z").getTime();
        return {
            last: rounds.filter(r => r.date && r.date >= cutoffStr),
            windowStartMs: startMs,
            windowEndMs: endMs,
        };
    }, [rounds]);

    const width = 600;
    const height = 200;
    const padding = 24;
    const xAxisSpace = 20;

    const windowMs = Math.max(windowEndMs - windowStartMs, DAY_MS);

    /** Map a date string to an x pixel, proportional to its calendar position. */
    const xScaleDate = (dateStr: string) => {
        const t = (new Date(dateStr + "T00:00:00Z").getTime() - windowStartMs) / windowMs;
        return padding + Math.max(0, Math.min(1, t)) * (width - padding * 2);
    };

    /**
     * Per-round x offset so R1/R2/R3 on the same day don't overlap.
     * Spreads dots by ~4 px per step around the day's base x.
     */
    const xScaleRound = (dateStr: string, roundIndex: number | undefined) => {
        const base = xScaleDate(dateStr);
        const step = 4;
        const ri = roundIndex ?? 0;
        return base + (ri - 1) * step;
    };

    const yScale = (p: number) => {
        const plotHeight = height - padding * 2 - xAxisSpace;
        return height - padding - xAxisSpace - (p / 5) * plotHeight;
    };

    // Three evenly-spaced date labels across the window
    const axisLabels = useMemo(() => {
        const labels = [];
        for (const frac of [0, 0.5, 1]) {
            const ms = windowStartMs + frac * windowMs;
            const dateStr = new Date(ms).toISOString().slice(0, 10);
            labels.push({dateStr, frac});
        }
        return labels;
    }, [windowStartMs, windowMs]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Points by round (last {DAYS_WINDOW} days)</div>
            <svg viewBox={`0 0 ${width} ${height}`} className="perf-line" role="img" aria-label="Points by round with axes">
                <defs>
                    <linearGradient id="perfLineGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.4"/>
                        <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0"/>
                    </linearGradient>
                </defs>
                <rect x="0" y="0" width={width} height={height} fill="transparent"/>
                {/* Y grid lines */}
                <g>
                    {[0, 1, 2, 3, 4, 5].map(p => (
                        <line key={p} x1={padding} x2={width - padding} y1={yScale(p)} y2={yScale(p)} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                    ))}
                </g>
                {/* Data line and points */}
                <g>
                    <polyline
                        fill="none"
                        stroke="var(--color-primary, #6366f1)"
                        strokeWidth="2"
                        points={last.map(r => `${xScaleRound(r.date!, r.roundIndex)},${yScale(r.points)}`).join(" ")}
                    />
                    {last.map((r) => (
                        <circle key={`pt-${r.date}-${r.roundIndex ?? 0}-${r.selectedBucket}`} cx={xScaleRound(r.date!, r.roundIndex)} cy={yScale(r.points)} r="2.5" fill="var(--color-primary, #6366f1)"/>
                    ))}
                </g>
                {/* Y axis labels */}
                <g aria-hidden="true">
                    {[0, 1, 2, 3, 4, 5].map(p => (
                        <text key={p} x={6} y={yScale(p) + 4} fontSize="11" fill="var(--color-muted)" textAnchor="start">{p}</text>
                    ))}
                    <text x={6} y={12} fontSize="11" fill="var(--color-muted)">Points</text>
                </g>
                {/* X axis with real date labels */}
                <g aria-hidden="true">
                    <line x1={padding} x2={width - padding} y1={height - padding} y2={height - padding} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                    {axisLabels.map(({dateStr, frac}) => (
                        <text
                            key={dateStr}
                            x={padding + frac * (width - padding * 2)}
                            y={height - padding + 14}
                            fontSize="11"
                            fill="var(--color-muted)"
                            textAnchor={frac === 0 ? "start" : frac === 1 ? "end" : "middle"}
                        >
                            {fmtDate(dateStr)}
                        </text>
                    ))}
                </g>
            </svg>
        </div>
    );
}


