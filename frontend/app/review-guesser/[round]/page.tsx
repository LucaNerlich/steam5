import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserRound from "../../../src/components/ReviewGuesserRound";
import Link from "next/link";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import {cookies} from 'next/headers';

export const dynamic = "force-dynamic";

async function loadToday(): Promise<ReviewGameState> {
    const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
    const res = await fetch(`${base}/api/review-game/today`, {
        headers: {"accept": "application/json"},
        cache: "no-store",
    });
    if (!res.ok) {
        throw new Error(`Failed to load daily picks: ${res.status}`);
    }
    return res.json();
}

async function loadMyGuesses(date: string): Promise<Record<number, {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket?: string,
    totalReviews?: number
}>> {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return {};
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/review-game/my/today`, {
        headers: {'accept': 'application/json', 'authorization': `Bearer ${token}`},
        cache: 'no-store'
    });
    if (!res.ok) return {};
    const data = await res.json() as Array<{
        roundIndex: number;
        appId: number;
        selectedBucket: string;
        actualBucket?: string,
        totalReviews?: number
    }>;
    const map: Record<number, {
        roundIndex: number;
        appId: number;
        selectedBucket: string;
        actualBucket?: string,
        totalReviews?: number
    }> = {};
    for (const g of data) map[g.roundIndex] = g;
    return map;
}

export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    const today = await loadToday();
    const my = await loadMyGuesses(today.date);

    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];
    const prefill = my[roundIndex] ? {
        selectedLabel: my[roundIndex].selectedBucket,
        actualBucket: my[roundIndex].actualBucket ?? '',
        totalReviews: my[roundIndex].totalReviews ?? 0,
    } : undefined;

    if (!pick) {
        return (
            <section className="container">
                <h1>Review Guesser</h1>
                <p>No pick for this round. You may have finished all rounds.</p>
                <Link href="/review-guesser/1">Go to first round</Link>
            </section>
        );
    }

    return (
        <section className="container">
            <ReviewGuesserHero today={today}
                               pick={pick}
                               roundIndex={roundIndex}/>

            <ReviewGuesserRound
                appId={pick.appId}
                buckets={today.buckets}
                roundIndex={roundIndex}
                totalRounds={totalRounds}
                pickName={pick.name}
                gameDate={today.date}
                prefilled={prefill}
                // pass all authenticated results to enable share button display without localStorage
                allResults={Object.fromEntries(Object.entries(my).map(([k, v]) => [Number(k), {
                    appId: v.appId,
                    pickName: today.picks[(Number(k) - 1)]?.name,
                    selectedLabel: v.selectedBucket,
                    actualBucket: v.actualBucket ?? '',
                    totalReviews: v.totalReviews ?? 0,
                    correct: v.actualBucket ? (v.actualBucket === v.selectedBucket) : false,
                }]))}
            />
        </section>
    );
}


