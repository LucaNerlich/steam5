import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import Link from "next/link";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import NewsBox from "@/components/NewsBox";
import ReviewGuesserRound from "@/components/ReviewGuesserRound";
import {Suspense} from "react";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

export const revalidate = 600;
// Render each round on demand, then cache it as static. force-static — combined
// with the page no longer reading the auth cookie — lets Next fully prefetch a
// round's content, so navigation resolves from cache instead of streaming through
// the loading skeleton. No build-time prerender is involved.
export const dynamic = 'force-static';

async function loadToday(): Promise<ReviewGameState> {
    const res = await fetch(`${backend}/api/review-game/today`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 600, tags: ['round-today']},
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
                <p>No pick for this round. You may have finished all rounds.</p>
                <Link href="/review-guesser/1">Go to first round</Link>
            </section>
        );
    }

    // Per-user guesses are loaded on the client (useServerGuesses) rather than here,
    // so this page stays free of dynamic inputs (no cookies / no-store) and can be
    // ISR-cached and fully prefetched — making round navigation instant instead of
    // re-rendering through the loading skeleton on every click.
    return (
        <section className="container">
            <ReviewGuesserHero today={today}
                               pick={pick}
                               roundIndex={roundIndex}/>

            <Suspense fallback={<div style={{height: 220, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <ReviewGuesserRound
                    appId={pick.appId}
                    buckets={today.buckets}
                    bucketTitles={today.bucketTitles}
                    roundIndex={roundIndex}
                    totalRounds={totalRounds}
                    pickName={pick.name}
                    gameDate={today.date}
                />
            </Suspense>

            {roundIndex === 1 && <NewsBox/>}

            <GameInfoSection pick={pick}/>
        </section>
    );
}

export async function generateMetadata({params}: { params: Promise<{ round: string }> }): Promise<Metadata> {
    const {round} = await params;
    try {
        const today: ReviewGameState = await fetch(`${backend}/api/review-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 600, tags: ['round-today']}
        }).then(r => r.json());
        const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
        const pick = today.picks[roundIndex - 1];
        const title = pick ? `Review Guesser — Round ${roundIndex} of ${today.picks.length}` : `Review Guesser`;
        const description = pick ? `${pick.name} — Can you guess how many Steam reviews it has?` : `Play today's Steam Review guessing game.`;
        const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
        const firstShot = pick?.screenshots?.[0];
        const rawImg = firstShot?.pathFull || firstShot?.pathThumbnail || '/opengraph-image';
        let imageUrl: string;
        try {
            imageUrl = new URL(rawImg).toString();
        } catch {
            imageUrl = new URL(rawImg.startsWith('/') ? rawImg : `/${rawImg}`, base).toString();
        }
        return {
            title,
            description,
            keywords: [
                'Steam',
                'Steam reviews',
                'review guessing game',
                'daily challenge',
                `round ${roundIndex}`,
                ...(pick?.name ? [pick.name] : [])
            ],
            alternates: {
                canonical: `/review-guesser/${roundIndex}`,
            },
            openGraph: {
                title,
                description,
                url: `/review-guesser/${roundIndex}`,
                images: [imageUrl],
            },
            twitter: {
                card: 'summary_large_image',
                title,
                description,
                images: [imageUrl],
            },
        };
    } catch (error) {
        console.error('Failed to load today', error);

        const title = `Review Guesser`;
        const description = `Play today's Steam Review guessing game.`;
        const ogUrl = new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, ''));
        ogUrl.pathname = '/opengraph-image';
        return {
            title,
            description,
            keywords: [
                'Steam',
                'Steam reviews',
                'review guessing game',
                'daily challenge'
            ],
            alternates: {
                canonical: `/review-guesser/${round || '1'}`,
            },
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


