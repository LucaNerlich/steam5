"use client";

import React, {useEffect, useState} from "react";

type Round = { date?: string };

function toUTCDate(d: string): number {
    // Parse yyyy-MM-dd as UTC midnight
    return new Date(d + "T00:00:00Z").getTime();
}

export default function StreaksCard({rounds}: { rounds: Round[] }): React.ReactElement {
    // UTC "current day" that refreshes at the next UTC midnight while mounted.
    const [todayUtc, setTodayUtc] = useState(() => new Date().toISOString().slice(0, 10));

    useEffect(() => {
        const scheduleNextMidnight = () => {
            const now = Date.now();
            const tomorrow = new Date(now);
            tomorrow.setUTCHours(24, 0, 0, 0);
            const msUntilMidnight = tomorrow.getTime() - now;
            return setTimeout(() => {
                setTodayUtc(new Date().toISOString().slice(0, 10));
                scheduleNextMidnight();
            }, msUntilMidnight);
        };
        const timer = scheduleNextMidnight();
        return () => clearTimeout(timer);
    }, []);

    const yesterdayUtc = (() => {
        const yesterday = new Date(new Date(todayUtc + "T00:00:00Z").getTime() - 86400000);
        return yesterday.toISOString().slice(0, 10);
    })();
    const {current, longest} = (() => {
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

        // A streak is only "current" if the player played today or yesterday.
        // If the last play date is older than that, the streak has already broken.
        const lastDate = dates[dates.length - 1];
        const current = lastDate >= yesterdayUtc && lastDate <= todayUtc ? tail : 0;

        return {current, longest: best};
    })();

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


