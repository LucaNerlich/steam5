import type {Metadata} from "next";
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

export async function generateMetadata({params}: { params: Promise<{ round: string }> }): Promise<Metadata> {
    const {round} = await params;
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    try {
        const today: ReviewGameState = await fetch(`${backend}/api/review-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 60}
        }).then(r => r.json());
        const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
        const pick = today.picks[roundIndex - 1];
        const title = pick ? `Review Guesser — Round ${roundIndex} of ${today.picks.length}` : `Review Guesser`;
        const description = pick ? `${pick.name} — Can you guess how many Steam reviews it has?` : `Play today's Steam Review guessing game.`;
        const ogUrl = new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, ''));
        ogUrl.pathname = '/opengraph-image';
        return {
            title,
            description,
            openGraph: {
                title,
                description,
                url: `/review-guesser/${roundIndex}`,
                images: [ogUrl.toString()],
            },
            twitter: {
                card: 'summary_large_image',
                title,
                description,
                images: [ogUrl.toString()],
            },
        };
    } catch (e) {
        const title = `Review Guesser`;
        const description = `Play today's Steam Review guessing game.`;
        const ogUrl = new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, ''));
        ogUrl.pathname = '/opengraph-image';
        return {
            title,
            description,
            openGraph: {
                title,
                description,
                url: `/review-guesser/${round || '1'}`,
                images: [ogUrl.toString()],
            },
            twitter: {
                card: 'summary_large_image',
                title,
                description,
                images: [ogUrl.toString()],
            },
        };
    }
}


