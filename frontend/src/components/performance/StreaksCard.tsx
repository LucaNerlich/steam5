"use client";

import React, {useMemo} from "react";

type Round = { date?: string };

function toUTCDate(d: string): number {
    // Parse yyyy-MM-dd as UTC midnight
    return new Date(d + "T00:00:00Z").getTime();
}

export default function StreaksCard({rounds}: { rounds: Round[] }): React.ReactElement {
    const {current, longest} = useMemo(() => {
        // Build unique played days from rounds
        const dateSet = new Set<string>();
        for (const r of rounds) if (r.date) dateSet.add(r.date);
        const dates = Array.from(dateSet);
        if (dates.length === 0) return {current: 0, longest: 0};
        // Sort ascending
        dates.sort((a, b) => toUTCDate(a) - toUTCDate(b));

        // Compute longest consecutive-day streak across all dates
        let best = 1;
        let run = 1;
        for (let i = 1; i < dates.length; i++) {
            const prev = toUTCDate(dates[i - 1]);
            const cur = toUTCDate(dates[i]);
            if (cur - prev === 24 * 60 * 60 * 1000) {
                run += 1;
                if (run > best) best = run;
            } else {
                run = 1;
            }
        }

        // Compute current streak from most recent day backwards
        let tail = 1;
        for (let i = dates.length - 1; i > 0; i--) {
            const prev = toUTCDate(dates[i - 1]);
            const cur = toUTCDate(dates[i]);
            if (cur - prev === 24 * 60 * 60 * 1000) tail += 1; else break;
        }

        return {current: tail, longest: best};
    }, [rounds]);

    return (
        <div className="perf-card">
            <div className="perf-card__title">Streaks</div>
            <dl className="stats-grid" aria-label="Current and longest daily play streaks">
                <dt>Current daily streak</dt>
                <dd>{current}</dd>
                <dt>Longest daily streak</dt>
                <dd>{longest}</dd>
            </dl>
        </div>
    );
}


