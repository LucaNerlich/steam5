"use client";

import React from "react";
import "@/styles/components/reviewRoundSummary.css";
import {scoreForRound} from "@/lib/scoring";

export default function RoundSummary(props: {
    buckets: string[];
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: {
        appId: number;
        pickName?: string;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    }
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest} = props;
    if (!gameDate) return null;

    type RoundResult = {
        pickName?: string;
        appId: number;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    };
    type Stored = { totalRounds: number; results: Record<number, RoundResult> };

    let data: Stored | null = null;
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
        data = raw ? JSON.parse(raw) as Stored : null;
    } catch {
        data = null;
    }
    if (!data || !data.results) return null;

    const indices = new Set(Object.keys(data.results).map(n => parseInt(n, 10)));
    indices.add(latestRound);
    const isComplete = indices.size >= totalRounds;
    if (!isComplete) return null;

    let total = 0;
    const bars: string[] = [];
    for (let i = 1; i <= totalRounds; i++) {
        const r = i === latestRound ? latest : data.results[i];
        if (!r) continue;
        const {bar, points} = scoreForRound(buckets, r.selectedLabel, r.actualBucket);
        total += points;
        bars.push(bar);
    }
    const maxTotal = 5 * totalRounds;

    return (
        <div className="review-round__summary" aria-live="polite">
            <div className="summary-bar">{bars.join('')}</div>
            <div className="summary-points"><strong>Score:</strong> {total}/{maxTotal}</div>
        </div>
    );
}


