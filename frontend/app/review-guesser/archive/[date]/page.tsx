import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import ArchiveOfflineRound from "@/components/ArchiveOfflineRound";
import ArchiveResetForDay from "@/components/ArchiveResetForDay";
import ArchiveSummary from "@/components/ArchiveSummary";
import Link from "next/link";
import {formatDate} from "@/lib/format";
import "@/styles/components/archive.css";
import GameInfoSection from "@/components/GameInfoSection";
import React from "react";

export const revalidate = 31536000;

async function loadArchived(date: string): Promise<ReviewGameState | null> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 31536000},
    });
    if (!res.ok) return null;
    return res.json();
}

async function loadAnswers(date: string, appIds: number[]): Promise<Record<number, {
    actualBucket: string;
    totalReviews: number
}>> {
    // Call the public guess endpoint per appId to get the bucket and review count without auth
    // This runs on the server during SSG, so it does not expose any secrets and avoids client requests.
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const out: Record<number, { actualBucket: string; totalReviews: number }> = {};
    await Promise.all(appIds.map(async (id) => {
        try {
            // We need bucket labels to query; however the backend guess API only needs appId and a label, but it returns the actual bucket regardless of correctness
            // To avoid leaking labels, we just pass an arbitrary label; the response includes the correct one (actualBucket)
            const res = await fetch(`${backend}/api/review-game/guess`, {
                method: 'POST',
                headers: {'content-type': 'application/json', 'accept': 'application/json'},
                body: JSON.stringify({appId: id, bucketGuess: "_"})
            });
            if (!res.ok) return;
            const data = await res.json() as { actualBucket: string; totalReviews: number };
            out[id] = {actualBucket: data.actualBucket, totalReviews: data.totalReviews};
        } catch {
            // ignore
        }
    }));
    return out;
}

export default async function ArchivePage({params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    const data = await loadArchived(date);
    if (!data || !data.picks || data.picks.length === 0) {
        return (
            <section className="container">
                <h1>Archive</h1>
                <p>No challenge found for {date}.</p>
                <Link href="/review-guesser/1">Go to today’s game</Link>
            </section>
        );
    }

    const appIds = data.picks.map(p => p.appId);
    const answers = await loadAnswers(date, appIds);

    return (
        <section className="container">
            <h1>Archive — {formatDate(date)}</h1>
            <ArchiveSummary
                date={date}
                bucketLabels={data.buckets}
                bucketTitles={data.bucketTitles || data.buckets}
            />
            <nav aria-label="Rounds table of contents" className="archive__toc">
                <ol>
                    {data.picks.map((app, idx) => (
                        <li key={`toc-${idx + 1}`}><a href={`#round-${idx + 1}`}>{app.name}</a></li>
                    ))}
                </ol>
            </nav>
            {data.picks.map((pick, idx) => (
                <div key={pick.appId} id={`round-${idx + 1}`} className="archive__round">
                    <ReviewGuesserHero today={data} pick={pick} roundIndex={idx + 1}/>
                    <ArchiveOfflineRound
                        appId={pick.appId}
                        buckets={data.buckets}
                        bucketTitles={data.bucketTitles}
                        roundIndex={idx + 1}
                        totalRounds={data.picks.length}
                        pickName={pick.name}
                        gameDate={date}
                        offlineAnswer={answers[pick.appId] ?? null}
                    />
                    <GameInfoSection pick={pick}/>
                </div>
            ))}
            <div className="archive__reset">
                <ArchiveResetForDay date={date}/>
            </div>
        </section>
    );
}

export async function generateMetadata({params}: { params: Promise<{ date: string }> }): Promise<Metadata> {
    const {date} = await params;
    const title = `Archive — ${formatDate(date)}`;
    const description = `Past daily challenge for ${formatDate(date)} — Steam Review Guesser.`;
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
    let ogUrl = '/opengraph-image';
    try {
        const day: ReviewGameState | null = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}`, {
            headers: { 'accept': 'application/json' },
            next: { revalidate: 31536000 }
        }).then(r => r.ok ? r.json() : null);
        const firstShot = day?.picks?.[0]?.screenshots?.[0];
        const rawImg = firstShot?.pathFull || firstShot?.pathThumbnail;
        if (rawImg) {
            try { ogUrl = new URL(rawImg).toString(); }
            catch { ogUrl = new URL(rawImg.startsWith('/') ? rawImg : `/${rawImg}`, base).toString(); }
        }
    } catch { /* ignore, fallback to default */ }
    return {
        title,
        description,
        keywords: [
            'Steam',
            'archive',
            'past challenges',
            'review guessing game',
            date
        ],
        alternates: {
            canonical: `/review-guesser/archive/${encodeURIComponent(date)}`,
        },
        openGraph: {
            title,
            description,
            url: `/review-guesser/archive/${encodeURIComponent(date)}`,
            images: [ogUrl],
        },
        twitter: {
            card: 'summary_large_image',
            title,
            description,
            images: [ogUrl],
        },
    };
}


