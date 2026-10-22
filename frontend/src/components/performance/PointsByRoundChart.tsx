"use client";

import React from "react";

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

function computeWindow(daysWindow: number, todayStr: string) {
    const cutoff = new Date(todayStr + "T00:00:00Z");
    cutoff.setUTCDate(cutoff.getUTCDate() - daysWindow);
    const cutoffStr = cutoff.toISOString().slice(0, 10);
    return {
        cutoffStr,
        startMs: new Date(cutoffStr + "T00:00:00Z").getTime(),
        endMs: new Date(todayStr + "T00:00:00Z").getTime(),
    };
}

/**
 * A usable text summary for a chart whose values otherwise exist only inside its
 * `role="img"` subtree (which assistive tech collapses to the label).
 */
function pointSummary(rounds: { points: number }[]): string {
    if (rounds.length === 0) return "no data";
    const pts = rounds.map(r => r.points);
    const avg = pts.reduce((a, b) => a + b, 0) / pts.length;
    return `${rounds.length} rounds, average ${avg.toFixed(2)} pts, range ${Math.min(...pts)}-${Math.max(...pts)}`;
}

export default function PointsByRoundChart({rounds, todayUtc}: { rounds: Round[]; todayUtc: string }): React.ReactElement {
    const DAYS_WINDOW = 30;

    const {cutoffStr, startMs: windowStartMs, endMs: windowEndMs} = computeWindow(DAYS_WINDOW, todayUtc);
    const last = rounds.filter(r => r.date && r.date >= cutoffStr && r.points != null);

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
    const axisLabels = (() => {
        const labels = [];
        for (const frac of [0, 0.5, 1]) {
            const ms = windowStartMs + frac * windowMs;
            const dateStr = new Date(ms).toISOString().slice(0, 10);
            labels.push({dateStr, frac});
        }
        return labels;
    })();

    return (
        <div className="perf-card">
            <div className="perf-card__title">Points by round (last {DAYS_WINDOW} days)</div>
            <svg viewBox={`0 0 ${width} ${height}`} className="perf-line" role="img" aria-label={`Points by round: ${pointSummary(last)}`}>
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


