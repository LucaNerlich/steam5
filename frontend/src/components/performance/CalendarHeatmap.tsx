"use client";

import React, {useMemo} from "react";

type Round = { date?: string; points: number };

const CELL = 11;
const GAP = 2;
const WEEKS = 53;
const DAYS = 7;
const LEFT = 20; // space for Mon/Wed/Fri labels
const TOP = 18;  // space for month labels

export default function CalendarHeatmap({rounds}: { rounds: Round[] }): React.ReactElement {
    const avgByDay = useMemo(() => {
        const map = new Map<string, { total: number; count: number }>();
        for (const r of rounds) {
            if (!r.date) continue;
            if (!map.has(r.date)) map.set(r.date, {total: 0, count: 0});
            const s = map.get(r.date)!;
            s.total += r.points;
            s.count++;
        }
        const result = new Map<string, number>();
        for (const [d, s] of map.entries()) result.set(d, s.total / s.count);
        return result;
    }, [rounds]);

    // Build 53×7 grid starting from Monday of the week 364 days ago
    const {cells, monthLabels} = useMemo(() => {
        const today = new Date();
        // Go back 364 days, then back to Monday
        const start = new Date(today);
        start.setUTCDate(start.getUTCDate() - 364);
        const dowStart = (start.getUTCDay() + 6) % 7; // 0=Mon
        start.setUTCDate(start.getUTCDate() - dowStart);

        const cells: {dateStr: string; week: number; day: number; avg: number | null}[] = [];
        const seenMonths = new Set<string>();
        const monthLabels: {week: number; label: string}[] = [];
        const cur = new Date(start);

        for (let w = 0; w < WEEKS; w++) {
            for (let d = 0; d < DAYS; d++) {
                const dateStr = cur.toISOString().slice(0, 10);
                const avg = avgByDay.get(dateStr) ?? null;
                cells.push({dateStr, week: w, day: d, avg});

                // Month label at start of week (day 0) when month changes
                if (d === 0) {
                    const mo = dateStr.slice(0, 7);
                    if (!seenMonths.has(mo)) {
                        seenMonths.add(mo);
                        const lbl = new Date(dateStr + "T00:00:00Z").toLocaleDateString("en-US", {
                            month: "short", timeZone: "UTC"
                        });
                        monthLabels.push({week: w, label: lbl});
                    }
                }
                cur.setUTCDate(cur.getUTCDate() + 1);
            }
        }
        return {cells, monthLabels};
    }, [avgByDay]);

    const svgW = LEFT + WEEKS * (CELL + GAP) - GAP;
    const svgH = TOP + DAYS * (CELL + GAP) - GAP;

    return (
        <div className="perf-card perf-card--full">
            <div className="perf-card__title">Play activity (last year)</div>
            <p style={{color: "var(--color-muted)", fontSize: "0.8rem", margin: "0 0 0.25rem"}}>
                Color intensity shows average points scored per round each day
            </p>
            <div className="perf-heatmap-wrap">
                <svg
                    viewBox={`0 0 ${svgW} ${svgH}`}
                    className="perf-line"
                    role="img"
                    aria-label="Calendar heatmap of play activity over the last year"
                    style={{minWidth: 400}}
                >
                    {/* Month labels */}
                    {monthLabels.map(({week, label}) => (
                        <text
                            key={`mo-${week}-${label}`}
                            x={LEFT + week * (CELL + GAP)}
                            y={TOP - 5}
                            fontSize="9"
                            fill="var(--color-muted)"
                        >{label}</text>
                    ))}
                    {/* Day labels: Mon, Wed, Fri */}
                    {([["M", 0], ["W", 2], ["F", 4]] as const).map(([lbl, di]) => (
                        <text
                            key={lbl}
                            x={LEFT - 3}
                            y={TOP + di * (CELL + GAP) + CELL * 0.82}
                            fontSize="8"
                            fill="var(--color-muted)"
                            textAnchor="end"
                        >{lbl}</text>
                    ))}
                    {/* Grid cells */}
                    {cells.map(({dateStr, week, day, avg}) => (
                        <rect
                            key={dateStr + "-" + day}
                            x={LEFT + week * (CELL + GAP)}
                            y={TOP + day * (CELL + GAP)}
                            width={CELL}
                            height={CELL}
                            rx={2}
                            fill={avg === null ? "var(--color-border)" : "var(--color-primary, #6366f1)"}
                            fillOpacity={avg === null ? 0.5 : 0.15 + (avg / 5) * 0.85}
                            aria-label={avg !== null ? `${dateStr}: ${avg.toFixed(1)} avg pts/round` : `${dateStr}: no play`}
                        >
                            <title>{avg !== null ? `${dateStr}\n${avg.toFixed(1)} avg pts/round` : `${dateStr}\nNo play`}</title>
                        </rect>
                    ))}
                </svg>
            </div>
            <div className="perf-heatmap-legend">
                <span style={{color: "var(--color-muted)", fontSize: "0.75rem"}}>Avg pts/round:</span>
                <span style={{color: "var(--color-muted)", fontSize: "0.75rem"}}>0</span>
                {[0, 0.25, 0.5, 0.75, 1].map(t => (
                    <span
                        key={t}
                        style={{
                            display: "inline-block",
                            width: 11,
                            height: 11,
                            borderRadius: 2,
                            background: t === 0 ? "var(--color-border)" : "var(--color-primary, #6366f1)",
                            opacity: t === 0 ? 0.5 : 0.15 + t * 0.85,
                            verticalAlign: "middle",
                        }}
                    />
                ))}
                <span style={{color: "var(--color-muted)", fontSize: "0.75rem"}}>5</span>
            </div>
        </div>
    );
}
