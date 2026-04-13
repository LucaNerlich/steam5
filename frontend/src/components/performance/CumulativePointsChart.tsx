"use client";

import React, {useMemo} from "react";

type Round = { points: number; date?: string };

const WIDTH = 600;
const HEIGHT = 200;
const PAD = {top: 16, right: 24, bottom: 30, left: 40};

function fmtDate(dateStr: string): string {
    return new Date(dateStr + "T00:00:00Z").toLocaleDateString("en-US", {
        month: "short", day: "numeric", timeZone: "UTC",
    });
}

export default function CumulativePointsChart({rounds}: { rounds: Round[] }): React.ReactElement {
    const {points, maxPts, dateLabels} = useMemo(() => {
        const byDay = new Map<string, number>();
        for (const r of rounds) {
            if (!r.date) continue;
            byDay.set(r.date, (byDay.get(r.date) ?? 0) + r.points);
        }
        const days = Array.from(byDay.entries()).sort(([a], [b]) => a.localeCompare(b));
        let cumulative = 0;
        const pts: { date: string; total: number }[] = [];
        for (const [date, dayPts] of days) {
            cumulative += dayPts;
            pts.push({date, total: cumulative});
        }

        const labels: { date: string; frac: number }[] = [];
        if (pts.length > 0) {
            labels.push({date: pts[0].date, frac: 0});
            if (pts.length > 2) {
                labels.push({date: pts[Math.floor(pts.length / 2)].date, frac: 0.5});
            }
            labels.push({date: pts[pts.length - 1].date, frac: 1});
        }

        return {
            points: pts,
            maxPts: pts.length > 0 ? pts[pts.length - 1].total : 0,
            dateLabels: labels,
        };
    }, [rounds]);

    if (points.length < 2) {
        return (
            <div className="perf-card perf-card--full">
                <div className="perf-card__title">Cumulative points</div>
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>
                    Need at least 2 days of data
                </p>
            </div>
        );
    }

    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const yMax = Math.max(maxPts, 1);

    const xScale = (i: number) => PAD.left + (i / (points.length - 1)) * plotW;
    const yScale = (v: number) => PAD.top + plotH - (v / yMax) * plotH;

    const polylinePoints = points.map((p, i) => `${xScale(i)},${yScale(p.total)}`).join(" ");
    const areaPoints = `${xScale(0)},${yScale(0)} ${polylinePoints} ${xScale(points.length - 1)},${yScale(0)}`;

    const yTicks = useMemo(() => {
        const step = Math.max(1, Math.ceil(yMax / 4));
        const ticks: number[] = [];
        for (let v = 0; v <= yMax; v += step) ticks.push(v);
        if (ticks[ticks.length - 1] < yMax) ticks.push(yMax);
        return ticks;
    }, [yMax]);

    return (
        <div className="perf-card perf-card--full">
            <div className="perf-card__title">Cumulative points</div>
            <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img" aria-label={`Cumulative points over time, total ${maxPts}`}>
                <defs>
                    <linearGradient id="cumulAreaGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.3"/>
                        <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.02"/>
                    </linearGradient>
                </defs>
                {/* Y grid */}
                {yTicks.map(v => (
                    <g key={v}>
                        <line x1={PAD.left} x2={WIDTH - PAD.right} y1={yScale(v)} y2={yScale(v)}
                              stroke="var(--color-border)" strokeOpacity="0.4" strokeWidth="1"/>
                        <text x={PAD.left - 4} y={yScale(v) + 4} fontSize="10" fill="var(--color-muted)" textAnchor="end">{v}</text>
                    </g>
                ))}
                {/* Area */}
                <polygon points={areaPoints} fill="url(#cumulAreaGrad)"/>
                {/* Line */}
                <polyline fill="none" stroke="var(--color-primary, #6366f1)" strokeWidth="2" points={polylinePoints}/>
                {/* Endpoint */}
                <circle cx={xScale(points.length - 1)} cy={yScale(maxPts)} r="3.5"
                        fill="var(--color-primary, #6366f1)"/>
                <text x={xScale(points.length - 1)} y={yScale(maxPts) - 8} fontSize="11"
                      fill="var(--color-primary, #6366f1)" textAnchor="middle" fontWeight="600">
                    {maxPts}
                </text>
                {/* X axis labels */}
                <g aria-hidden="true">
                    <line x1={PAD.left} x2={WIDTH - PAD.right} y1={PAD.top + plotH} y2={PAD.top + plotH}
                          stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                    {dateLabels.map(({date, frac}) => (
                        <text key={date} x={PAD.left + frac * plotW}
                              y={HEIGHT - PAD.bottom + 14} fontSize="11" fill="var(--color-muted)"
                              textAnchor={frac === 0 ? "start" : frac === 1 ? "end" : "middle"}>
                            {fmtDate(date)}
                        </text>
                    ))}
                </g>
            </svg>
        </div>
    );
}
