import type {ReviewGameState} from "@/types/review-game";
import Link from "next/link";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import NewsBox from "@/components/NewsBox";
import ReviewGuesserRound from "@/components/ReviewGuesserRound";

export const revalidate = 60;

async function loadToday(): Promise<ReviewGameState> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/review-game/today`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 60},
    });
    if (!res.ok) {
        throw new Error(`Failed to load daily picks: ${res.status}`);
    }
    return res.json();
}

export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    const today = await loadToday();

    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];

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

            {roundIndex === 1 && <NewsBox/>}

            <ReviewGuesserRound
                appId={pick.appId}
                buckets={today.buckets}
                bucketTitles={today.bucketTitles}
                roundIndex={roundIndex}
                totalRounds={totalRounds}
                pickName={pick.name}
                gameDate={today.date}
            />

            <GameInfoSection pick={pick}/>
        </section>
    );
}


