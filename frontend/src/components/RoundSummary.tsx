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
    };
    // Optional: provide full results when user is authenticated (server-provided),
    // so we don't rely on localStorage only
    results?: Record<number, {
        pickName?: string;
        appId: number;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    }>
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest, results} = props;
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

    let stored: Stored | null = null;
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
        stored = raw ? JSON.parse(raw) as Stored : null;
    } catch {
        stored = null;
    }

    const merged: Record<number, RoundResult> = {
        ...(stored?.results || {}),
        ...(results || {}),
        [latestRound]: latest,
    };

    const isComplete = Object.keys(merged).length >= totalRounds;
    if (!isComplete) return null;

    let total = 0;
    const bars: string[] = [];
    for (let i = 1; i <= totalRounds; i++) {
        const r = merged[i] || (i === latestRound ? latest : undefined);
        if (!r) continue;
        const {bar, points} = scoreForRound(buckets, r.selectedLabel, r.actualBucket);
        total += points;
        bars.push(bar);
    }
    const maxTotal = 5 * totalRounds;

    return (
        <>
            <div className="review-round__summary" aria-live="polite">
                <div className="summary-bar">{bars.join('')}</div>
                <div className="summary-points"><strong>Score:</strong> {total}/{maxTotal}</div>
            </div>
        </>
    );
}


