"use client";

import React from "react";
import "@/styles/components/reviewRoundPoints.css";

function scoreForRound(buckets: string[], selectedLabel: string, actual: string): {
    bar: string;
    points: number;
    distance: number
} {
    const selectedIndex = buckets.indexOf(selectedLabel);
    const actualIndex = buckets.indexOf(actual);
    if (selectedIndex < 0 || actualIndex < 0) return {bar: '⬜', points: 0, distance: 0};
    const d = Math.abs(selectedIndex - actualIndex);
    const maxPoints = 5;
    const step = 2;
    const points = Math.max(0, maxPoints - step * d);
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
    const {bar, points, distance} = scoreForRound(buckets, selectedLabel, actualBucket);
    return (
        <p className="review-round__points">
            <span className="points-emoji" aria-hidden="true">{bar}</span>
            <strong>&nbsp;{points}</strong> {points === 1 ? 'point' : 'points'} — {distance === 0 ? 'exact match' : `off by ${distance}`}
        </p>
    );
}


