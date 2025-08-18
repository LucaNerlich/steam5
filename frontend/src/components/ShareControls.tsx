"use client";

import React, {useState} from "react";
import "@/styles/components/reviewShareControls.css";
import {scoreForRound} from "@/lib/scoring";

type RoundResult = {
    pickName?: string;
    appId: number;
    selectedLabel: string;
    actualBucket: string;
    totalReviews: number;
    correct: boolean
};

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
    inline?: boolean,
    // Optional: provide full results when user is authenticated (server-provided),
    // so we don't rely on localStorage
    results?: Record<number, RoundResult>
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest, inline, results} = props;
    const [copied, setCopied] = useState(false);
    const isLocalhost = typeof window !== 'undefined' && (location.hostname === 'localhost' || location.hostname === '127.0.0.1');

    if (!gameDate) return null;

    type Stored = { totalRounds: number; results: Record<number, RoundResult> };

    let data: Stored | null = null;
    if (results && Object.keys(results).length > 0) {
        data = {totalRounds, results};
    } else {
        try {
            const raw = typeof window !== 'undefined' ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
            data = raw ? JSON.parse(raw) as Stored : null;
        } catch {
            data = null;
        }
    }

    if (!data || !data.results) return null;
    const indices = new Set(Object.keys(data.results).map(n => parseInt(n, 10)));
    indices.add(latestRound);
    const isComplete = indices.size >= totalRounds;

    async function syncLocalGuessesToBackend() {
        try {
            if (!isLocalhost) return;
            // Only attempt when we do NOT have server results
            const hasServer = results && Object.keys(results).length > 0;
            if (hasServer) return;
            const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
            for (const i of Array.from(indices)) {
                const r = (i === latestRound ? latest : data!.results[i]);
                if (!r) continue;
                // Fire-and-forget; backend will ignore duplicates
                await fetch(`${backend}/api/review-game/guess`, {
                    method: 'POST',
                    headers: {'content-type': 'application/json', 'accept': 'application/json'},
                    body: JSON.stringify({appId: r.appId, bucketGuess: r.selectedLabel}),
                    cache: 'no-store'
                }).catch(() => {
                });
            }
        } catch {
            // ignore sync errors
        }
    }

    // Best-effort sync in the background when summary/share is possible
    if (isLocalhost) {
        // no await; avoid blocking render
        void syncLocalGuessesToBackend();
    }
    if (!isComplete) return null;

    const lines: string[] = [];
    const title = `Steam5 | Review Game — ${gameDate}`;
    lines.push(title);
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
    lines.push('');
    lines.push('Play: https://steam5.org/review-guesser');
    lines.push('Leaderboard: https://steam5.org/review-guesser/leaderboard');
    const text = lines.join('\n');

    async function copyToClipboard() {
        const copyWithTextarea = () => {
            try {
                const textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.setAttribute('readonly', '');
                textarea.style.position = 'fixed';
                textarea.style.left = '-9999px';
                document.body.appendChild(textarea);
                textarea.select();
                document.execCommand('copy');
                document.body.removeChild(textarea);
                return true;
            } catch {
                return false;
            }
        };

        try {
            if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
                setCopied(true);
                setTimeout(() => setCopied(false), 1500);
                return;
            }
        } catch {
            // fall through to textarea fallback
        }
        if (copyWithTextarea()) {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        }
    }

    if (inline) {
        return (
            <div className="share-inline">
                <button type="button" className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        );
    }
    return (
        <div className="review-round__share">
            <div className="share-inline">
                <button type="button" className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        </div>
    );
}


