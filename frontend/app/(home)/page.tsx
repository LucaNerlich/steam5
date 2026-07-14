import type {Metadata} from "next";
import Link from "next/link";
import {Routes} from "../routes";
import HomeReviewHero from "@/components/HomeReviewHero";
import GameTeaserCard from "@/components/GameTeaserCard";
import type {ReviewGameState} from "@/types/review-game";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";
import "@/styles/components/home.css";

export const revalidate = 60;

async function loadToday(): Promise<ReviewGameState | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 60, tags: ['home-today']},
        });
        if (!res.ok) return null;
        return res.json();
    } catch {
        return null;
    }
}

export default async function Home() {
    const today = await loadToday();
    const firstPick = today?.picks?.[0];

    return (
        <section className="container home">
            {today && firstPick ? (
                <HomeReviewHero today={today} pick={firstPick}/>
            ) : (
                <div className="home__fallback">
                    <p>Today&apos;s review challenge is loading — or the backend is unavailable.</p>
                    <Link href={Routes.reviewGuesser1} className="btn-cta">Play Review Guesser</Link>
                </div>
            )}

            <h2 className="home__secondary-title">More games on the way</h2>
            <div className="home__secondary">
                <GameTeaserCard
                    title="Release Year Guesser"
                    description="Three shorter daily rounds. Look at screenshots and details, then guess which release-year bucket the game belongs in."
                    href={Routes.yearGuesser}
                    badge="Coming soon"
                    icon="📅"
                    hintPreview="Guess the year freely; far-off misses unlock tiered hints for fewer points."
                />
                <GameTeaserCard
                    title="Price Guesser"
                    description="Guess the price tier for Steam games. No need to nail the exact cent amount — pick the right bucket instead."
                    href={Routes.priceGuesser}
                    badge="Coming soon"
                    icon="💲"
                    hintPreview="Reveal discount, currency, or formatted price step by step for fewer points."
                />
            </div>
        </section>
    );
}

export const metadata: Metadata = {
    title: 'Steam5 — Daily Steam guessing games',
    description: 'Play Review Guesser today. Guess Steam review counts, then try Release Year and Price Guesser when they launch.',
    alternates: {
        canonical: '/',
    },
    keywords: [
        'Steam',
        'Steam reviews',
        'guessing game',
        'daily game',
        'review counts',
        'release year',
        'price guesser',
        'leaderboard',
        'browser game',
    ],
    openGraph: {
        title: 'Steam5 — Daily Steam guessing games',
        description: 'Review Guesser is live. Release Year and Price Guesser are coming soon with tiered hints.',
        url: '/',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5 — Daily Steam guessing games',
        description: 'Review Guesser is live. Release Year and Price Guesser are coming soon with tiered hints.',
        images: ['/opengraph-image'],
    },
};
