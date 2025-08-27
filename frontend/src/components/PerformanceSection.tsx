"use client";

import React, {useMemo, useCallback} from "react";

type Round = {
    roundIndex: number;
    appId: number;
    appName?: string;
    selectedBucket: string;
    actualBucket: string;
    points: number;
};

type Day = {
    date: string;
    rounds: Round[];
};

export default function PerformanceSection({days}: { days: Day[] }): React.ReactElement | null {
    const rounds = useMemo(() => days.flatMap(d => d.rounds.map(r => ({...r, date: d.date}))), [days]);
    const hasRounds = rounds.length > 0;

    // Normalize last N rounds for charts
    const WINDOW_ROUNDS = 35;
    const last = rounds.slice(-WINDOW_ROUNDS);
    const width = 600;
    const height = 160;
    const padding = 24;
    const xAxisSpace = 20; // reserved space below plot for X-axis labels

    const xScale = (i: number) => {
        if (last.length <= 1) return padding;
        const t = i / (last.length - 1);
        return padding + t * (width - padding * 2);
    };
    const yScale = (p: number) => {
        const min = 0;
        const max = 5; // max points per round
        const t = (p - min) / (max - min);
        const plotHeight = height - padding * 2 - xAxisSpace;
        return height - padding - xAxisSpace - t * plotHeight;
    };

    // (removed unused linePath)

    const bars = useMemo(() => {
        const counts = {hit: 0, high: 0, low: 0};
        for (const r of last) {
            if (r.selectedBucket === r.actualBucket) counts.hit++;
            else if (r.selectedBucket > r.actualBucket) counts.high++;
            else counts.low++;
        }
        const total = Math.max(1, counts.hit + counts.high + counts.low);
        return {
            counts,
            parts: [
                {key: "Hit", value: counts.hit, color: "var(--color-success, #16a34a)"},
                {key: "Too High", value: counts.high, color: "var(--color-warning, #f59e0b)"},
                {key: "Too Low", value: counts.low, color: "var(--color-danger, #ef4444)"},
            ].map(p => ({...p, pct: p.value / total})),
        };
    }, [last]);

    const sparkWidth = width; // align coordinate system with main chart to avoid oversized labels when scaled
    const sparkHeight = 110;
    const sparkPad = 6;

    // Bottom graph: rolling hit rate over last WINDOW_ROUNDS rounds
    const sparkSeries = useMemo(() => {
        const windowSize = WINDOW_ROUNDS;
        const vals: number[] = [];
        for (let i = 0; i < last.length; i++) {
            const s = Math.max(0, i - windowSize + 1);
            const slice = last.slice(s, i + 1);
            const hits = slice.filter(r => r.selectedBucket === r.actualBucket).length;
            vals.push(Math.round((hits / slice.length) * 100));
        }
        return {values: vals, min: 0, max: 100, label: 'Hit rate (%)'} as const;
    }, [last]);

    const sparkY = useCallback((v: number) => {
        const min = sparkSeries.min, max = sparkSeries.max;
        const t = (v - min) / Math.max(1, (max - min));
        return sparkHeight - sparkPad - t * (sparkHeight - sparkPad * 2);
    }, [sparkSeries.min, sparkSeries.max, sparkHeight, sparkPad]);
    const sparkPath = useMemo(() => sparkSeries.values.map((v, i) => `${i === 0 ? 'M' : 'L'}${sparkPad + (i / Math.max(1, sparkSeries.values.length - 1)) * (sparkWidth - sparkPad * 2)},${sparkY(v)}`).join(' '), [sparkSeries.values, sparkWidth, sparkY]);

    if (!hasRounds) return null;

    return (
        <section aria-labelledby="perf-title" className="profile__performance">
            <h2 id="perf-title">Recent performance</h2>
            <div className="perf-grid">
                <div className="perf-card">
                    <div className="perf-card__title">Points by round (last {WINDOW_ROUNDS})</div>
                    <svg viewBox={`0 0 ${width} ${height}`} className="perf-line" role="img" aria-label="Points by round with axes">
                        <defs>
                            <linearGradient id="perfLineGrad" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0.4"/>
                                <stop offset="100%" stopColor="var(--color-primary, #6366f1)" stopOpacity="0"/>
                            </linearGradient>
                        </defs>
                        <rect x="0" y="0" width={width} height={height} fill="transparent"/>
                        <g>
                            <polyline
                                fill="none"
                                stroke="var(--color-primary, #6366f1)"
                                strokeWidth="2"
                                points={last.map((r, i) => `${xScale(i)},${yScale(r.points)}`).join(" ")}
                            />
                            {last.map((r, i) => (
                                <circle key={i} cx={xScale(i)} cy={yScale(r.points)} r="2.5"
                                        fill="var(--color-primary, #6366f1)"/>
                            ))}
                        </g>
                        <g>
                            {[0, 1, 2, 3, 4, 5].map(p => (
                                <line key={p} x1={padding} x2={width - padding} y1={yScale(p)} y2={yScale(p)}
                                      stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                            ))}
                        </g>
                        {/* Y-axis tick labels */}
                        <g aria-hidden="true">
                            {[0, 1, 2, 3, 4, 5].map(p => (
                                <text key={p} x={6} y={yScale(p) + 4} fontSize="11" fill="var(--color-muted)" textAnchor="start">{p}</text>
                            ))}
                            <text x={6} y={12} fontSize="11" fill="var(--color-muted)">Points</text>
                        </g>
                        {/* X-axis guide and label */}
                        <g aria-hidden="true">
                            <line x1={padding} x2={width - padding} y1={height - padding} y2={height - padding}
                                  stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1"/>
                            {(() => {
                                const len = Math.max(2, last.length);
                                const idxs = [0, Math.floor((len - 1) / 2), len - 1];
                                return idxs.map((idx, i) => (
                                    <text key={i} x={xScale(idx)} y={height - padding + 14} fontSize="11" fill="var(--color-muted)" textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}>
                                        {i === 0 ? 'older' : i === 1 ? 'mid' : 'newer'}
                                    </text>
                                ));
                            })()}
                        </g>
                    </svg>
                </div>

                <div className="perf-card">
                    <div className="perf-card__title">Outcome mix (last {WINDOW_ROUNDS})</div>
                    <div className="perf-stacked" role="img" aria-label="Distribution of outcomes">
                        {bars.parts.map(part => (
                            <div key={part.key} className="perf-stacked__part" style={{width: `${part.pct * 100}%`, background: part.color}}/>
                        ))}
                    </div>
                    <div className="perf-legend">
                        {bars.parts.map(p => (
                            <div key={p.key} className="perf-legend__item">
                                <span className="dot" style={{background: p.color}}/>
                                <span>{p.key}: {p.value}</span>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="perf-card">
                    <div className="perf-card__title">Hit rate (rolling last {WINDOW_ROUNDS})</div>
                    <svg viewBox={`0 0 ${sparkWidth} ${sparkHeight}`} className="perf-spark" role="img" aria-label="Rolling hit rate with axes">
                        {/* Y-axis ticks and labels at 0/25/50/75/100 */}
                        {([0,25,50,75,100] as const).map(p => (
                            <g key={p}>
                                <line x1={padding} x2={sparkWidth - padding} y1={sparkY(p)} y2={sparkY(p)} stroke="var(--color-border)" strokeOpacity="0.5" strokeWidth="1" />
                                <text x={padding - 4} y={sparkY(p) + 3} fontSize="10" fill="var(--color-muted)" textAnchor="end">{p}%</text>
                            </g>
                        ))}
                        {/* Clip path to avoid overlapping Y-axis labels */}
                        <defs>
                            <clipPath id="sparkClip">
                                <rect x={padding} y={0} width={sparkWidth - padding * 2} height={sparkHeight} />
                            </clipPath>
                        </defs>
                        <g clipPath="url(#sparkClip)">
                            <polyline fill="none" stroke="var(--color-primary, #6366f1)" strokeWidth="2" points={sparkPath.replace(/M|L/g, '').trim()} />
                        </g>
                        {/* X axis labels (small) */}
                        {(() => {
                            const len = Math.max(2, sparkSeries.values.length);
                            const idxs = [0, Math.floor((len - 1) / 2), len - 1];
                            return idxs.map((idx, i) => (
                                <text key={i} x={padding + (idx / Math.max(1, len - 1)) * (sparkWidth - padding * 2)} y={sparkHeight - 2} fontSize="10" fill="var(--color-muted)" textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}>
                                    {i === 0 ? 'older' : i === 1 ? 'mid' : 'newer'}
                                </text>
                            ));
                        })()}
                    </svg>
                    <div style={{color: 'var(--color-muted)', fontSize: 12, marginTop: 4}}>Hit rate (%) over a rolling {WINDOW_ROUNDS}-round window.</div>
                </div>
            </div>
        </section>
    );
}


