"use client";

import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import NewsBox from "@/components/NewsBox";
import ReviewGuesserRound from "@/components/ReviewGuesserRound";
import RoundLoadingSkeleton from "../../app/review-guesser/[round]/loading";

interface Props {
    round: number;
}

// Loads today's picks with a plain client fetch (no SSR, no caching of the round
// HTML). Shows a skeleton while fetching, then renders the hero + round from the
// fresh data — so the page always reflects today's backend picks and submissions
// resolve against the matching app.
export default function ReviewGuesserClient({round}: Props) {
    const [today, setToday] = useState<ReviewGameState | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const res = await fetch('/api/review-game/today', {
                cache: 'no-store',
                headers: {accept: 'application/json'},
            });
            if (!res.ok) throw new Error(`Failed to load daily picks: ${res.status}`);
            const data: ReviewGameState = await res.json();
            setToday(data);
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void load();
    }, [load]);

    if (loading) {
        return <RoundLoadingSkeleton/>;
    }

    if (error || !today) {
        return (
            <section className="container">
                <p>Couldn&apos;t load today&apos;s round.</p>
                <button type="button" className="btn-link" onClick={() => void load()}>Retry</button>
            </section>
        );
    }

    const totalRounds = today.picks.length;
    const pick = today.picks[round - 1];

    if (!pick) {
        return (
            <section className="container">
                <p>No pick for this round. You may have finished all rounds.</p>
                <Link href="/review-guesser/1">Go to first round</Link>
            </section>
        );
    }

    return (
        <section className="container">
            <ReviewGuesserHero today={today}
                               pick={pick}
                               roundIndex={round}/>

            <ReviewGuesserRound
                appId={pick.appId}
                buckets={today.buckets}
                bucketTitles={today.bucketTitles}
                roundIndex={round}
                totalRounds={totalRounds}
                pickName={pick.name}
                gameDate={today.date}
            />

            {round === 1 && <NewsBox/>}

            <GameInfoSection pick={pick}/>
        </section>
    );
}
