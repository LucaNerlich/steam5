import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import NewsBox from "@/components/NewsBox";
import PlayerSpotlight from "@/components/PlayerSpotlight";
import ReviewGuesserRound from "@/components/ReviewGuesserRound";
import {Suspense} from "react";
import {cookies} from "next/headers";
import {notFound} from "next/navigation";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

export const revalidate = 60;

async function loadToday(): Promise<ReviewGameState> {
    const res = await fetch(`${backend}/api/review-game/today`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 60, tags: ['round-today']},
    });
    if (!res.ok) {
        throw new Error(`Failed to load daily picks: ${res.status}`);
    }
    return res.json();
}

type ServerGuess = {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket?: string;
    totalReviews?: number;
};

async function loadMyGuesses(): Promise<ServerGuess[]> {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return [];
    try {
        const res = await fetch(`${backend}/api/review-game/my/today`, {
            headers: {"accept": "application/json", "authorization": `Bearer ${token}`},
            // Do not cache; user-specific
            cache: 'no-store',
        });
        if (!res.ok) return [];
        return await res.json();
    } catch {
        return [];
    }
}

export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const parsedRound = Number.parseInt(round || '1', 10);
    if (!Number.isInteger(parsedRound) || parsedRound < 1) {
        notFound();
    }
    const roundIndex = parsedRound;
    const today = await loadToday();

    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];

    if (!pick) {
        notFound();
    }

    // Preload existing guesses to avoid client-side flicker for authenticated users.
    // Only keep a guess if its appId still matches today's pick for that round — when
    // picks are regenerated for the same date, stale guesses would otherwise be shown
    // (they remain dated today on the backend but point at the previous game).
    const myGuesses = await loadMyGuesses();
    const appIdForRound = (ri: number): number | undefined => today.picks[ri - 1]?.appId;
    const freshGuesses = myGuesses.filter(g => g.appId === appIdForRound(g.roundIndex));
    const allResults = Object.fromEntries(freshGuesses.map(g => [g.roundIndex, {
        appId: g.appId,
        pickName: undefined,
        selectedLabel: g.selectedBucket,
        actualBucket: g.actualBucket ?? '',
        totalReviews: g.totalReviews ?? 0,
        correct: g.actualBucket ? (g.actualBucket === g.selectedBucket) : false,
    }]));
    const currentPrefill = (() => {
        const g = freshGuesses.find(x => x.roundIndex === roundIndex);
        if (!g) return undefined;
        return {
            selectedLabel: g.selectedBucket,
            actualBucket: g.actualBucket ?? '',
            totalReviews: g.totalReviews ?? 0,
        } as const;
    })();

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
                    dayGames={today.picks.map((p) => ({appId: p.appId, name: p.name}))}
                    prefilled={currentPrefill}
                    allResults={allResults}
                />
            </Suspense>

            {roundIndex === 1 && <PlayerSpotlight/>}
            {roundIndex === 1 && <NewsBox/>}

            <GameInfoSection pick={pick}/>
        </section>
    );
}

export async function generateMetadata({params}: { params: Promise<{ round: string }> }): Promise<Metadata> {
    const {round} = await params;
    const parsedRound = Number.parseInt(round || '1', 10);
    if (!Number.isInteger(parsedRound) || parsedRound < 1) {
        notFound();
    }
    const roundIndex = parsedRound;
    let today: ReviewGameState;
    try {
        today = await fetch(`${backend}/api/review-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 60, tags: ['round-today']}
        }).then(r => r.json());
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
                canonical: `/review-guesser/${round}`,
            },
            openGraph: {
                title,
                description,
                url: `/review-guesser/${round}`,
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

    const pick = today.picks[roundIndex - 1];
    if (!pick) {
        notFound();
    }
    const title = `Review Guesser — Round ${roundIndex} of ${today.picks.length}`;
    const description = `${pick.name} — Can you guess how many Steam reviews it has?`;
    const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
    const firstShot = pick.screenshots?.[0];
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
            pick.name
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
}


