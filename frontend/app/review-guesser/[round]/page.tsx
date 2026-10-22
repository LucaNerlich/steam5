import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import NewsBox from "@/components/NewsBox";
import PlayerSpotlight from "@/components/PlayerSpotlight";
import ReviewGuesserRound from "@/components/ReviewGuesserRound";
import {notFound} from "next/navigation";
import {BACKEND_ORIGIN as backend, BACKEND_TIMEOUT_MS } from "@/lib/backend";

export const revalidate = 60;

async function loadToday(): Promise<ReviewGameState> {
    const res = await fetch(`${backend}/api/review-game/today`, {
        signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
        headers: {"accept": "application/json"},
        next: {revalidate: 60, tags: ['round-today']},
    });
    if (!res.ok) {
        throw new Error(`Failed to load daily picks: ${res.status}`);
    }
    return res.json();
}

export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const roundIndex = parseRoundIndex(round);
    if (roundIndex === null) {
        notFound();
    }
    const today = await loadToday();

    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];

    if (!pick) {
        notFound();
    }

    // No cookies()/bearer fetch here: the page stays statically renderable and the
    // useServerGuesses client hook restores the player's guesses after mount.

    return (
        <section className="container">
            <ReviewGuesserHero today={today}
                               pick={pick}
                               roundIndex={roundIndex}/>

            <ReviewGuesserRound
                appId={pick.appId}
                buckets={today.buckets}
                bucketTitles={today.bucketTitles}
                roundIndex={roundIndex}
                totalRounds={totalRounds}
                pickName={pick.name}
                gameDate={today.date}
                dayGames={today.picks.map((p) => ({appId: p.appId, name: p.name}))}
            />

            {roundIndex === 1 && <PlayerSpotlight/>}
            {roundIndex === 1 && <NewsBox/>}

            <GameInfoSection pick={pick}/>
        </section>
    );
}

export async function generateMetadata({params}: { params: Promise<{ round: string }> }): Promise<Metadata> {
    const {round} = await params;
    const roundIndex = parseRoundIndex(round);
    if (roundIndex === null) {
        notFound();
    }
    let today: ReviewGameState;
    try {
        // Reuse loadToday() so non-2xx responses (or unparseable bodies) are
        // treated as failures and fall back to the generic metadata below —
        // parsing the body of a 400/500 would leave today.picks undefined and
        // throw outside this try block.
        today = await loadToday();
    } catch (error) {
        console.error('Failed to load today', error);

        const title = `Review Guesser`;
        const description = `Play today's Steam Review guessing game.`;
        const ogUrl = new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, ''));
        ogUrl.pathname = '/og';
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
    const rawImg = firstShot?.pathFull || firstShot?.pathThumbnail || '/og';
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


