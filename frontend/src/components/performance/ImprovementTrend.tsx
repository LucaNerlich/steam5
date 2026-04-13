"use client";

import React, {useMemo} from "react";

type Round = { points: number; date?: string };

function avgPoints(rs: Round[]): number {
    return rs.reduce((s, r) => s + r.points, 0) / rs.length;
}

export default function ImprovementTrend({rounds}: { rounds: Round[] }): React.ReactElement {
    const {early, recent, delta, third, earlyLabel, recentLabel} = useMemo(() => {
        if (rounds.length < 6) return {early: null, recent: null, delta: null, third: 0, earlyLabel: "", recentLabel: ""};
        const t = Math.floor(rounds.length / 3);
        const earlySlice = rounds.slice(0, t);
        const recentSlice = rounds.slice(rounds.length - t);
        const e = avgPoints(earlySlice);
        const r = avgPoints(recentSlice);

        const fmtLabel = (slice: Round[]): string => {
            const dates = slice.map(rd => rd.date).filter(Boolean) as string[];
            if (dates.length >= 2) {
                const first = dates[0];
                const last = dates[dates.length - 1];
                const fmt = (d: string) => {
                    const parts = d.split("-");
                    return `${parts[1]}/${parts[2]}`;
                };
                if (first === last) return fmt(first);
                return `${fmt(first)} – ${fmt(last)}`;
            }
            return "";
        };

        return {early: e, recent: r, delta: r - e, third: t, earlyLabel: fmtLabel(earlySlice), recentLabel: fmtLabel(recentSlice)};
    }, [rounds]);

    const noData = delta === null;
    const improving = !noData && delta > 0.1;
    const declining = !noData && delta < -0.1;

    const trendColor = noData
        ? "var(--color-muted)"
        : improving
            ? "var(--color-success, #16a34a)"
            : declining
                ? "var(--color-danger, #ef4444)"
                : "var(--color-muted)";

    const trendLabel = noData ? "Need more data" : improving ? "Improving" : declining ? "Declining" : "Stable";
    const trendArrow = noData ? "" : improving ? "▲" : declining ? "▼" : "▶";

    const WIDTH = 300;
    const HEIGHT = 100;
    const PAD = {top: 14, right: 24, bottom: 14, left: 24};
    const plotW = WIDTH - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const yScale = (v: number) => PAD.top + plotH - (Math.max(0, Math.min(5, v)) / 5) * plotH;

    const subtitle = noData
        ? null
        : `First ${third} vs last ${third} of ${rounds.length} rounds`;

    return (
        <div className="perf-card">
            <div className="perf-card__title">Improvement trend</div>
            {noData ? (
                <p style={{color: "var(--color-muted)", fontSize: "0.85rem", margin: "0.25rem 0"}}>
                    Not enough data (need at least 6 rounds)
                </p>
            ) : (
                <>
                    <p style={{color: "var(--color-muted)", fontSize: "0.75rem", margin: "0.15rem 0 0.35rem", lineHeight: 1.3}}>
                        {subtitle}
                    </p>
                    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="perf-line" role="img"
                         aria-label={`Improvement trend: first ${third} rounds avg ${early!.toFixed(2)}, last ${third} rounds avg ${recent!.toFixed(2)}`}>
                        {/* Reference lines */}
                        {[0, 5].map(v => (
                            <line key={v} x1={PAD.left} x2={WIDTH - PAD.right} y1={yScale(v)} y2={yScale(v)}
                                  stroke="var(--color-border)" strokeOpacity="0.4" strokeWidth="1"/>
                        ))}
                        {/* Connecting line */}
                        <line
                            x1={PAD.left + plotW * 0.18}
                            y1={yScale(early!)}
                            x2={PAD.left + plotW * 0.82}
                            y2={yScale(recent!)}
                            stroke={trendColor}
                            strokeWidth="2"
                            strokeDasharray={improving || declining ? "none" : "4 3"}
                            opacity="0.7"
                        />
                        {/* Early dot */}
                        <circle cx={PAD.left + plotW * 0.18} cy={yScale(early!)} r="5"
                                fill="var(--color-primary, #6366f1)" fillOpacity="0.7"/>
                        <text x={PAD.left + plotW * 0.18} y={yScale(early!) - 8} fontSize="10"
                              fill="var(--color-muted)" textAnchor="middle">
                            {earlyLabel ? `First ${third}` : "First ⅓"}
                        </text>
                        <text x={PAD.left + plotW * 0.18} y={yScale(early!) + 16} fontSize="12"
                              fill="var(--color-primary, #6366f1)" textAnchor="middle" fontWeight="600">
                            {early!.toFixed(2)}
                        </text>
                        {earlyLabel && (
                            <text x={PAD.left + plotW * 0.18} y={yScale(early!) + 28} fontSize="9"
                                  fill="var(--color-muted)" textAnchor="middle" opacity="0.8">
                                {earlyLabel}
                            </text>
                        )}
                        {/* Recent dot */}
                        <circle cx={PAD.left + plotW * 0.82} cy={yScale(recent!)} r="5"
                                fill={trendColor} fillOpacity="0.9"/>
                        <text x={PAD.left + plotW * 0.82} y={yScale(recent!) - 8} fontSize="10"
                              fill="var(--color-muted)" textAnchor="middle">
                            {recentLabel ? `Last ${third}` : "Last ⅓"}
                        </text>
                        <text x={PAD.left + plotW * 0.82} y={yScale(recent!) + 16} fontSize="12"
                              fill={trendColor} textAnchor="middle" fontWeight="600">
                            {recent!.toFixed(2)}
                        </text>
                        {recentLabel && (
                            <text x={PAD.left + plotW * 0.82} y={yScale(recent!) + 28} fontSize="9"
                                  fill="var(--color-muted)" textAnchor="middle" opacity="0.8">
                                {recentLabel}
                            </text>
                        )}
                        {/* Trend label */}
                        <text x={WIDTH / 2} y={HEIGHT - 1} fontSize="11" fill={trendColor}
                              textAnchor="middle" fontWeight="600">
                            {trendArrow} {trendLabel} ({delta! >= 0 ? "+" : ""}{delta!.toFixed(2)})
                        </text>
                    </svg>
                </>
            )}
        </div>
    );
}
