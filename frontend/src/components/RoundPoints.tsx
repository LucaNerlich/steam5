"use client";

import React from "react";
import "@/styles/components/reviewRoundPoints.css";

const MAX_POINTS = 5;
const POINTS_PLURAL_RULES = new Intl.PluralRules("en-US");

function scoreForRound(buckets: string[], selectedLabel: string, actual: string): {
    points: number;
    distance: number
} {
    const selectedIndex = buckets.indexOf(selectedLabel);
    const actualIndex = buckets.indexOf(actual);
    if (selectedIndex < 0 || actualIndex < 0) return {points: 0, distance: 0};
    const d = Math.abs(selectedIndex - actualIndex);
    const step = 2;
    const points = Math.max(0, MAX_POINTS - step * d);
    return {points, distance: d};
}

export default function RoundPoints({buckets, selectedLabel, actualBucket}: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    actualBucket: string
}) {
    if (!selectedLabel) return null;
    const {points} = scoreForRound(buckets, selectedLabel, actualBucket);
    const pointsLabel = POINTS_PLURAL_RULES.select(points) === "one" ? "point" : "points";

    return (
        <div className="result-points">
            <div className="result-points__text">
                <strong>{points}</strong> {pointsLabel}
            </div>
        </div>
    );
}
