"use client";

import React from "react";
import "@/styles/components/reviewRoundPoints.css";

const MAX_POINTS = 5;

const DISTANCE_CLASS = ['exact', 'close', 'near', 'far'] as const;

function scoreForRound(buckets: string[], selectedLabel: string, actual: string): {
    bar: string;
    points: number;
    distance: number
} {
    const selectedIndex = buckets.indexOf(selectedLabel);
    const actualIndex = buckets.indexOf(actual);
    if (selectedIndex < 0 || actualIndex < 0) return {bar: '⬜', points: 0, distance: 0};
    const d = Math.abs(selectedIndex - actualIndex);
    const step = 2;
    const points = Math.max(0, MAX_POINTS - step * d);
    const emojiByDistance = ['🟩', '🟨', '🟧'];
    const bar = d <= 2 ? emojiByDistance[d] : '🟥';
    return {bar, points, distance: d};
}

export default function RoundPoints({buckets, selectedLabel, actualBucket}: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    actualBucket: string
}) {
    if (!selectedLabel) return null;
    const {points, distance} = scoreForRound(buckets, selectedLabel, actualBucket);
    const distanceKey = DISTANCE_CLASS[Math.min(distance, 3)];

    return (
        <div className="result-points">
            <meter
                className={`result-points__meter result-points__meter--${distanceKey}`}
                min={0}
                max={MAX_POINTS}
                value={points}
            />
            <div className="result-points__text">
                <strong>{points}</strong> {points === 1 ? 'pt' : 'pts'}
                <span className="result-points__sep">&middot;</span>
                {distance === 0 ? 'exact match' : `off by ${distance}`}
            </div>
        </div>
    );
}
