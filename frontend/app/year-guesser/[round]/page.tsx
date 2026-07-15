import type {Metadata} from "next";
import type {YearGameState} from "@/types/year-game";
import type {MyYearGuess} from "@/types/year-game";
import Link from "next/link";
import YearGuesserHero from "@/components/YearGuesserHero";
import GameInfoSection from "@/components/GameInfoSection";
import YearGuesserRound from "@/components/YearGuesserRound";
import {Suspense} from "react";
import {cookies} from "next/headers";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

export const revalidate = 60;

async function loadToday(): Promise<YearGameState> {
    const res = await fetch(`${backend}/api/year-game/today`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 60, tags: ['year-round-today']},
    });
    if (!res.ok) {
        throw new Error(`Failed to load year-game picks: ${res.status}`);
    }
    return res.json();
}

async function loadMyGuesses(): Promise<MyYearGuess[]> {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return [];
    try {
        const res = await fetch(`${backend}/api/year-game/my/today`, {
            headers: {"accept": "application/json", "authorization": `Bearer ${token}`},
            cache: 'no-store',
        });
        if (!res.ok) return [];
        return await res.json();
    } catch {
        return [];
    }
}

export default async function YearGuesserRoundPage({params}: {params: Promise<{round: string}>}) {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    const today = await loadToday();
    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];

    if (!pick) {
        return (
            <section className="container">
                <p>No pick for this round.</p>
                <Link href="/year-guesser/1">Go to first round</Link>
            </section>
        );
    }

    const myGuesses = await loadMyGuesses();
    const appIdForRound = (index: number): number | undefined => today.picks[index - 1]?.appId;
    const serverGuess = myGuesses.find(
        (guess) => guess.roundIndex === roundIndex && guess.appId === appIdForRound(roundIndex),
    );

    return (
        <section className="container">
            <YearGuesserHero today={today} pick={pick} roundIndex={roundIndex}/>

            <Suspense fallback={<div style={{height: 220, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <YearGuesserRound
                    key={`${today.date}-${roundIndex}`}
                    appId={pick.appId}
                    hintTiers={today.hintTiers}
                    roundIndex={roundIndex}
                    totalRounds={totalRounds}
                    pickName={pick.name}
                    gameDate={today.date}
                    serverGuess={serverGuess}
                />
            </Suspense>

            <GameInfoSection pick={pick}/>
        </section>
    );
}

export async function generateMetadata({params}: {params: Promise<{round: string}>}): Promise<Metadata> {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    try {
        const today: YearGameState = await fetch(`${backend}/api/year-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 60, tags: ['year-round-today']},
        }).then((response) => response.json());
        const pick = today.picks[roundIndex - 1];
        const title = pick
            ? `Year Guesser — Round ${roundIndex} of ${today.picks.length}`
            : 'Release Year Guesser';
        const description = pick
            ? `${pick.name} — When did this game release on Steam?`
            : `Guess Steam release years in one daily round.`;
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
            alternates: {canonical: `/year-guesser/${roundIndex}`},
            openGraph: {title, description, url: `/year-guesser/${roundIndex}`, images: [imageUrl]},
            twitter: {card: 'summary_large_image', title, description, images: [imageUrl]},
        };
    } catch {
        return {
            title: 'Release Year Guesser',
            description: 'Guess Steam release years in one daily round.',
            alternates: {canonical: `/year-guesser/${round || '1'}`},
        };
    }
}
