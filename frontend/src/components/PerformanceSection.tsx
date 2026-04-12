"use client";

import React, {useMemo} from "react";
import PointsByRoundChart from "@/components/performance/PointsByRoundChart";
import OutcomeMixBar from "./performance/OutcomeMixBar";
import RollingHitRateSpark from "./performance/RollingHitRateSpark";
import BucketAccuracyBars from "./performance/BucketAccuracyBars";
import StreaksCard from "./performance/StreaksCard";
import HitRateVsAverageCard from "./performance/HitRateVsAverageCard";
import CalendarHeatmap from "./performance/CalendarHeatmap";
import RoundPositionChart from "./performance/RoundPositionChart";
import DayOfWeekChart from "./performance/DayOfWeekChart";
import ImprovementTrend from "./performance/ImprovementTrend";
import PerfectDaysCard from "./performance/PerfectDaysCard";
import BiasBadge from "./performance/BiasBadge";
import BucketCallouts from "./performance/BucketCallouts";

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
    const rounds = useMemo(() => {
        const flat = days.flatMap(d => d.rounds.map(r => ({...r, date: d.date})));
        // Ensure chronological order: oldest -> newest, then by roundIndex
        return flat.sort((a: any, b: any) => {
            if (a.date === b.date) return (a.roundIndex||0) - (b.roundIndex||0);
            return a.date.localeCompare(b.date);
        });
    }, [days]);
    if (rounds.length === 0) return null;

    return (
        <section aria-labelledby="perf-title">
            <h2 id="perf-title">Recent performance</h2>
            <div className="perf-grid">
                <CalendarHeatmap rounds={rounds} />
                <PointsByRoundChart rounds={rounds} />
                <OutcomeMixBar rounds={rounds} />
                <RollingHitRateSpark rounds={rounds} />
                <StreaksCard rounds={rounds} />
                <BucketAccuracyBars rounds={rounds} />
                <HitRateVsAverageCard rounds={rounds} />
                <RoundPositionChart rounds={rounds} />
                <DayOfWeekChart rounds={rounds} />
                <ImprovementTrend rounds={rounds} />
                <PerfectDaysCard rounds={rounds} />
                <BiasBadge rounds={rounds} />
                <BucketCallouts rounds={rounds} />
            </div>
        </section>
    );
}
