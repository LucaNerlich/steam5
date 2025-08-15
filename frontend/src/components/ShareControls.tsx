"use client";

import React, {useState} from "react";
import "@/styles/components/reviewShareControls.css";

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

export default function ShareControls(props: {
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
    },
    inline?: boolean
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest, inline} = props;
    const [copied, setCopied] = useState(false);

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

    const lines: string[] = [];
    lines.push(`https://steam5.org/review-guesser - Steam Review Game — ${gameDate}`);
    let total = 0;
    const bars: string[] = [];
    for (let i = 1; i <= totalRounds; i++) {
        const r = i === latestRound ? latest : data.results[i];
        if (!r) continue;
        const {bar, points, distance} = scoreForRound(buckets, r.selectedLabel, r.actualBucket);
        total += points;
        bars.push(bar);
        lines.push(`${bar} | Round ${i}: ${r.pickName ?? 'App ' + r.appId} — off by ${distance}`);
    }
    if (bars.length > 0) {
        const maxTotal = 5 * totalRounds;
        lines.splice(1, 0, `${bars.join('')} ${total}/${maxTotal}`, '');
    }
    lines.push(`Total points: ${total}`);
    const text = lines.join('\n');

    async function copyToClipboard() {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        } catch {
            /* no-op */
        }
    }

    if (inline) {
        return (
            <div className="share-inline">
                <button className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        );
    }
    return (
        <div className="review-round__share">
            <div className="share-inline">
                <button className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        </div>
    );
}


