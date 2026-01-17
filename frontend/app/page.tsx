import type {Metadata} from "next";
import Link from "next/link";
import {Routes} from "./routes";

export default function Home() {
    return (
        <section>
            <h1>Steam5 <em>work-in-progress</em></h1>

            <section className="container">
                <h1>Games</h1>
                <ul>
                    <li><Link href={Routes.reviewGuesser}>Review Guesser</Link></li>
                </ul>
            </section>
        </section>
    );
}

export const metadata: Metadata = {
    title: 'Play Review Guesser — Daily Steam review game',
    description: 'Guess how many reviews a Steam game has. Five daily rounds, shareable results, and leaderboards on Steam5.',
    alternates: {
        canonical: '/',
    },
    keywords: [
        'Steam',
        'Steam reviews',
        'guessing game',
        'daily game',
        'review counts',
        'leaderboard',
        'free',
        'browser game',
        'no download',
        'web-based',
        'daily puzzle'
    ],
    openGraph: {
        title: 'Steam5 — Daily Steam Review Guesser',
        description: 'Guess Steam review counts across five daily rounds. Share and compete on the leaderboard.',
        url: '/',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5 — Daily Steam Review Guesser',
        description: 'Guess Steam review counts across five daily rounds. Share and compete on the leaderboard.',
        images: ['/opengraph-image'],
    },
};
