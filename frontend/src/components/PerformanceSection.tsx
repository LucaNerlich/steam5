"use client";

import React, {useMemo} from "react";
import PointsByRoundChart from "@/components/performance/PointsByRoundChart";
import OutcomeMixBar from "./performance/OutcomeMixBar";
import RollingHitRateSpark from "./performance/RollingHitRateSpark";
import BucketAccuracyBars from "./performance/BucketAccuracyBars";
import StreaksCard from "./performance/StreaksCard";
import HitRateVsAverageCard from "./performance/HitRateVsAverageCard";

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
    if (rounds.length === 0) return null;

    return (
        <section aria-labelledby="perf-title" className="profile__performance">
            <h2 id="perf-title">Recent performance</h2>
            <div className="perf-grid">
                <PointsByRoundChart rounds={rounds} />
                <OutcomeMixBar rounds={rounds} />
                <RollingHitRateSpark rounds={rounds} />
                <StreaksCard rounds={rounds} />
                <BucketAccuracyBars rounds={rounds} />
                <HitRateVsAverageCard rounds={rounds} />
            </div>
        </section>
    );
}


