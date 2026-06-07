"use client";

import React from "react";
import PointsByRoundChart from "@/components/performance/PointsByRoundChart";
import type {FlatRound} from "@/lib/rounds";
import OutcomeMixBar from "./performance/OutcomeMixBar";
import RollingHitRateSpark from "./performance/RollingHitRateSpark";
import BucketAccuracyBars from "./performance/BucketAccuracyBars";
import StreaksCard from "./performance/StreaksCard";
import HitRateVsAverageCard from "./performance/HitRateVsAverageCard";
import CalendarHeatmap from "./performance/CalendarHeatmap";
import RoundPositionChart from "./performance/RoundPositionChart";
import DayOfWeekChart from "./performance/DayOfWeekChart";
import ImprovementTrend from "./performance/ImprovementTrend";
import BiasBadge from "./performance/BiasBadge";
import BucketCallouts from "./performance/BucketCallouts";

export default function PerformanceSection({rounds}: { rounds: FlatRound[] }): React.ReactElement | null {
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
                <BiasBadge rounds={rounds} />
                <BucketCallouts rounds={rounds} />
            </div>
        </section>
    );
}
